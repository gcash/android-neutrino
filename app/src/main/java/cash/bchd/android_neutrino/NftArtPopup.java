package cash.bchd.android_neutrino;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
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

import cash.bchd.android_neutrino.tokens.TokenIcons;
import cash.bchd.android_neutrino.wallet.Wallet;

/**
 * NftArtPopup shows an NFT's art large, in a popup over its screen: what's saved of it at once,
 * then its full image, video or animation, which only opening the popup fetches, as it can be
 * megabytes, and which is kept in the cache for the next time.
 */
final class NftArtPopup {

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
            if (file.exists()) {
                showFull(screen, layout, view, popup, file, size);
            } else {
                // Art that moves has nothing to show for it but a still, which needs saying.
                boolean moves = TokenIcons.moves(screen, category, commitment, uri);
                fetch(screen, layout, view, popup, category, moves, uri, file, size);
            }
        }
        return popup;
    }

    private static void fetch(Activity screen, ViewGroup layout, View view, PopupWindow popup, String category,
                              boolean moves, String uri, File file, int size) {
        Wallet wallet = Wallet.getInstance();
        if (wallet == null || !wallet.isRunning()) {
            if (moves) {
                failed(screen, layout, view, popup);
            }
            return;
        }
        View loading = view.findViewById(R.id.nftArtLoading);
        loading.setVisibility(View.VISIBLE);
        int side = Math.min(screen.getResources().getDisplayMetrics().widthPixels,
                screen.getResources().getDisplayMetrics().heightPixels);
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
                view.post(() -> {
                    loading.setVisibility(View.GONE);
                    if (done && popup.isShowing()) {
                        showFull(screen, layout, view, popup, file, size);
                    } else if (moves) {
                        failed(screen, layout, view, popup);
                    }
                });
            }

            @Override
            public void onFailure(Throwable t) {
                // What's saved of the art stays, which is the art at a smaller size.
                boolean busy = Wallet.gatewaysBusy(t);
                view.post(() -> {
                    loading.setVisibility(View.GONE);
                    if (busy && popup.isShowing()) {
                        Snackbar.make(layout, R.string.gateways_busy, Snackbar.LENGTH_LONG).show();
                    } else if (moves) {
                        failed(screen, layout, view, popup);
                    }
                });
            }
        }, MoreExecutors.directExecutor());
    }

    // showFull shows the file: a video, muted and over and over, or else an image or an animation.
    private static void showFull(Activity screen, ViewGroup layout, View view, PopupWindow popup, File file, int size) {
        if (TokenIcons.isVideo(file)) {
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
            return;
        }
        ImageView image = view.findViewById(R.id.nftArtImage);
        new Thread(() -> {
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
        }, "nft-art").start();
    }

    private static void failed(Activity screen, ViewGroup layout, View view, PopupWindow popup) {
        view.findViewById(R.id.nftArtVideo).setVisibility(View.GONE);
        if (popup.isShowing()) {
            Snackbar.make(layout, R.string.nft_play_failed, Snackbar.LENGTH_LONG).show();
        }
    }
}
