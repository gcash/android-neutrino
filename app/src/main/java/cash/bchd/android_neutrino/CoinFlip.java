package cash.bchd.android_neutrino;

import android.content.Context;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.io.IOException;

/**
 * CoinFlip flips a coin once in a popup whose QR code was paid, in place of the code and its
 * text.
 */
final class CoinFlip {

    private static final String TAG = "coinFlip";

    private CoinFlip() {
    }

    /**
     * show hides what the popup's layout shows and flips the coin there, once however many
     * payments arrive. The layout keeps its width and takes the coin's height.
     */
    static void show(LinearLayout layout) {
        if (layout.findViewWithTag(TAG) != null) {
            return;
        }
        for (int i = 0; i < layout.getChildCount(); i++) {
            layout.getChildAt(i).setVisibility(View.GONE);
        }
        ViewGroup.LayoutParams params = layout.getLayoutParams();
        params.width = layout.getWidth();
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        layout.setLayoutParams(params);
        View coin = view(layout.getContext());
        coin.setTag(TAG);
        layout.addView(coin, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    // view returns a view that spans its width, with the coin's height at that width, and plays
    // the GIF once, ending on its last frame.
    private static View view(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                Drawable coin = ImageDecoder.decodeDrawable(
                        ImageDecoder.createSource(context.getResources(), R.drawable.coinflip));
                ImageView view = new ImageView(context);
                view.setAdjustViewBounds(true);
                view.setScaleType(ImageView.ScaleType.FIT_CENTER);
                view.setImageDrawable(coin);
                if (coin instanceof AnimatedImageDrawable) {
                    AnimatedImageDrawable flip = (AnimatedImageDrawable) coin;
                    flip.setRepeatCount(0);
                    flip.start();
                }
                return view;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        GifView view = new GifView(context);
        view.setGifImageDrawableId(R.drawable.coinflip);
        view.drawGif();
        return view;
    }
}
