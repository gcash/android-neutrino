package cash.bchd.android_neutrino;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Movie;
import android.view.View;

import java.io.InputStream;

// Movie, which Android 9 deprecated for AnimatedImageDrawable, is the only way to play a GIF
// before it, so CoinFlip uses this view on Android 7 and 8 only.
@SuppressWarnings("deprecation")
public class GifView extends View {

    private InputStream inputStream;

    private Movie movie;

    private int gifImageDrawableId;

    private Context ctx = null;

    private long gifStart = 0;

    private boolean complete;

    public GifView(Context context) {
        super(context);

        // Make the custom view focus.
        setFocusable(true);

        ctx = context;
    }

    // It spans the width it gets, with the GIF's height at that width.
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = movie == null ? 0 : width * movie.height() / movie.width();
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = System.currentTimeMillis();

        if (gifStart == 0) {
            gifStart = now;
        }

        if (movie != null) {
            // Get gif movie duration time.
            int duration = movie.duration();

            if (duration == 0) {
                duration = 1000;
            }

            // Get played frame percentage.
            int relTime = (int)((now - gifStart) % duration);
            if (complete) {
                relTime = 1700;
            }

            // Set current gif frame time.
            movie.setTime(relTime);

            // Scale the frame to fit the view and center it, as an ImageView's fitCenter does.
            float scale = Math.min((float) getWidth() / movie.width(), (float) getHeight() / movie.height());
            canvas.translate((getWidth() - movie.width() * scale) / 2, (getHeight() - movie.height() * scale) / 2);
            canvas.scale(scale, scale);
            movie.draw(canvas, 0, 0);

            // This method will invoke onDraw method.
            invalidate();
            if (relTime > 1700) {
                complete = true;
            }
        }
    }

    public int getGifImageDrawableId() {
        return gifImageDrawableId;
    }

    public void setGifImageDrawableId(int gifImageDrawableId) {
        this.gifImageDrawableId = gifImageDrawableId;
    }

    // Call this method to read the drawable gif image to create movie object.
    public void drawGif()
    {
        Resources resources = ctx.getResources();

        inputStream = resources.openRawResource(gifImageDrawableId);

        movie = Movie.decodeStream(inputStream);

        // Invalidate the view and invoke onDraw method.
        invalidate();
    }
}