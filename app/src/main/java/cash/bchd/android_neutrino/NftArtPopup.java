package cash.bchd.android_neutrino;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.VideoView;

import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;

import cash.bchd.android_neutrino.tokens.TokenIcons;
import cash.bchd.android_neutrino.wallet.Wallet;

/**
 * NftArtPopup shows an NFT's art large, in a popup over its screen: what's saved of it at once,
 * then its full image, video or animation, which only opening the popup fetches, as it can be
 * megabytes, and which is kept in the cache for the next time.
 */
final class NftArtPopup {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private NftArtPopup() {
    }

    /**
     * show shows the art, with the full file at the URI if there is one, and returns the popup.
     */
    static PopupWindow show(Activity screen, ViewGroup layout, String category, String commitment, String uri) {
        View view = LayoutInflater.from(screen).inflate(R.layout.nft_art_popup, layout, false);
        int margin = screen.getResources().getDimensionPixelSize(R.dimen.tx_details_margin);
        int size = Math.min(layout.getWidth(), layout.getHeight()) - 2 * margin;
        ImageView image = view.findViewById(R.id.nftArtImage);
        // Drawn off the main thread, as a large image takes a while to decode.
        TokenIcons.showNft(image, category, commitment, size);

        PopupWindow popup = new PopupWindow(view, size, size, true);
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        view.setOnClickListener(v -> popup.dismiss());
        view.setContentDescription(screen.getString(R.string.nft_art_close));
        VideoView video = view.findViewById(R.id.nftArtVideo);
        popup.setOnDismissListener(video::stopPlayback);
        popup.showAtLocation(layout, Gravity.CENTER, 0, 0);

        if (uri != null) {
            File file = TokenIcons.fullFile(screen, category, commitment, uri);
            // Off the main thread, as it reads the art's files.
            new Thread(() -> {
                boolean saved = file.exists();
                // Art that moves has nothing to show for it but a still, which needs saying.
                boolean moves = !saved && TokenIcons.moves(screen, category, commitment, uri);
                view.post(() -> {
                    if (!popup.isShowing()) {
                        return;
                    }
                    if (saved) {
                        showFull(screen, layout, view, popup, file, size);
                    } else {
                        fetch(screen, layout, view, popup, category, moves, uri, file, size);
                    }
                });
            }, "nft-art").start();
        }
        return popup;
    }

    // fetch downloads the full file and saves it, whether the popup is still open when it comes or
    // not, as the user asked for it, and opening the popup again then shows it at once. The popup
    // and its screen are held only weakly meanwhile, as the download has no deadline of its own.
    private static void fetch(Activity screen, ViewGroup layout, View view, PopupWindow popup, String category,
                              boolean moves, String uri, File file, int size) {
        Wallet wallet = Wallet.getInstance();
        if (wallet == null || !wallet.isRunning()) {
            if (moves) {
                failed(screen, layout, view, popup);
            }
            return;
        }
        view.findViewById(R.id.nftArtLoading).setVisibility(View.VISIBLE);
        int side = Math.min(screen.getResources().getDisplayMetrics().widthPixels,
                screen.getResources().getDisplayMetrics().heightPixels);
        Shown shown = new Shown(screen, layout, view, popup);
        Futures.addCallback(wallet.tokenIconAsync(uri, true), new FutureCallback<byte[]>() {
            @Override
            public void onSuccess(byte[] data) {
                boolean saved = false;
                try {
                    saved = TokenIcons.saveFull(category, file, data, side);
                } catch (RuntimeException | OutOfMemoryError e) {
                    e.printStackTrace();
                }
                boolean done = saved;
                MAIN.post(() -> shown.then((s, l, v, p) -> {
                    v.findViewById(R.id.nftArtLoading).setVisibility(View.GONE);
                    if (done && p.isShowing()) {
                        showFull(s, l, v, p, file, size);
                    } else if (moves) {
                        failed(s, l, v, p);
                    }
                }));
            }

            @Override
            public void onFailure(Throwable t) {
                // What's saved of the art stays, which is the art at a smaller size.
                boolean busy = Wallet.gatewaysBusy(t);
                MAIN.post(() -> shown.then((s, l, v, p) -> {
                    v.findViewById(R.id.nftArtLoading).setVisibility(View.GONE);
                    if (busy && p.isShowing()) {
                        Snackbar.make(l, R.string.gateways_busy, Snackbar.LENGTH_LONG).show();
                    } else if (moves) {
                        failed(s, l, v, p);
                    }
                }));
            }
        }, MoreExecutors.directExecutor());
    }

    // Shown is the popup and the screen it shows on, held weakly, so that a download that ends
    // after they're gone keeps neither, and does only what saves the file.
    private static final class Shown {
        interface Then {
            void run(Activity screen, ViewGroup layout, View view, PopupWindow popup);
        }

        private final WeakReference<Activity> screen;
        private final WeakReference<ViewGroup> layout;
        private final WeakReference<View> view;
        private final WeakReference<PopupWindow> popup;

        Shown(Activity screen, ViewGroup layout, View view, PopupWindow popup) {
            this.screen = new WeakReference<>(screen);
            this.layout = new WeakReference<>(layout);
            this.view = new WeakReference<>(view);
            this.popup = new WeakReference<>(popup);
        }

        // then runs on the main thread with them, if they're all still there.
        void then(Then then) {
            Activity s = screen.get();
            ViewGroup l = layout.get();
            View v = view.get();
            PopupWindow p = popup.get();
            if (s != null && l != null && v != null && p != null && !s.isDestroyed()) {
                then.run(s, l, v, p);
            }
        }
    }

    // showFull shows the file: a video, muted and over and over, or else an image or an animation,
    // which it reads and decodes off the main thread, as it can be large.
    private static void showFull(Activity screen, ViewGroup layout, View view, PopupWindow popup, File file, int size) {
        new Thread(() -> {
            if (TokenIcons.isVideo(file)) {
                view.post(() -> {
                    if (popup.isShowing()) {
                        play(screen, layout, view, popup, file);
                    }
                });
                return;
            }
            decode(screen, layout, view, popup, file, size);
        }, "nft-art").start();
    }

    // play plays the video, muted and over and over.
    private static void play(Activity screen, ViewGroup layout, View view, PopupWindow popup, File file) {
        VideoView video = view.findViewById(R.id.nftArtVideo);
        video.setVisibility(View.VISIBLE);
        video.setOnPreparedListener(player -> {
            player.setVolume(0, 0);
            player.setLooping(true);
        });
        video.setOnErrorListener((player, what, extra) -> {
            file.delete();
            failed(screen, layout, view, popup);
            return true;
        });
        // It plays muted, so it leaves whatever else is playing alone.
        video.setAudioFocusRequest(AudioManager.AUDIOFOCUS_NONE);
        video.setVideoPath(file.getPath());
        video.start();
    }

    // decode shows the image or animation, which it decodes on the thread it's called on.
    private static void decode(Activity screen, ViewGroup layout, View view, PopupWindow popup, File file, int size) {
        ImageView image = view.findViewById(R.id.nftArtImage);
        try {
            Drawable drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(file),
                    (decoder, info, source) -> {
                        int[] fitted = TokenIcons.fitted(info.getSize().getWidth(), info.getSize().getHeight(), size);
                        decoder.setTargetSize(fitted[0], fitted[1]);
                    });
            view.post(() -> {
                if (!popup.isShowing()) {
                    return;
                }
                // The smaller art, if it's still being drawn, doesn't replace this.
                TokenIcons.clear(image);
                image.setImageDrawable(drawable);
                if (drawable instanceof AnimatedImageDrawable) {
                    ((AnimatedImageDrawable) drawable).start();
                }
            });
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            file.delete();
            view.post(() -> failed(screen, layout, view, popup));
        }
    }

    private static void failed(Activity screen, ViewGroup layout, View view, PopupWindow popup) {
        view.findViewById(R.id.nftArtVideo).setVisibility(View.GONE);
        if (popup.isShowing()) {
            Snackbar.make(layout, R.string.nft_play_failed, Snackbar.LENGTH_LONG).show();
        }
    }
}
