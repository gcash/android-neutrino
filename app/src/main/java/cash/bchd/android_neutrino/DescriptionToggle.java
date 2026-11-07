package cash.bchd.android_neutrino;

import android.annotation.SuppressLint;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.TextView;

/**
 * DescriptionToggle has a description show its first lines, and all of them on a tap, and its first
 * lines again on another.
 */
final class DescriptionToggle {

    private static final int LINES = 3;
    // What a description in a fixed header opens to, where it scrolls in place, as the screen's
    // list still has to show.
    private static final int FIXED_LINES = 10;

    private DescriptionToggle() {
    }

    /**
     * attach has the description open on a tap, in a header that doesn't scroll if fixed.
     */
    @SuppressLint("ClickableViewAccessibility")
    static void attach(TextView description, boolean fixed) {
        boolean[] open = {false};
        // Scrolling an open description isn't a tap, though lifting a finger inside it is a click.
        boolean[] dragged = {false};
        float[] startY = {0};
        int slop = ViewConfiguration.get(description.getContext()).getScaledTouchSlop();
        description.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                dragged[0] = false;
                startY[0] = event.getY();
            } else if (Math.abs(event.getY() - startY[0]) > slop) {
                dragged[0] = true;
            }
            return false;
        });
        description.setOnClickListener(v -> {
            if (dragged[0]) {
                dragged[0] = false;
                return;
            }
            open[0] = !open[0];
            description.setMaxLines(open[0] ? (fixed ? FIXED_LINES : Integer.MAX_VALUE) : LINES);
            description.setEllipsize(open[0] ? null : TextUtils.TruncateAt.END);
            if (!fixed) {
                return;
            }
            description.setMovementMethod(open[0] ? ScrollingMovementMethod.getInstance() : null);
            description.scrollTo(0, 0);
            if (!open[0]) {
                // Clearing the movement leaves the view unclickable, and text that can't ellipsize.
                description.setClickable(true);
                description.setText(description.getText().toString());
            }
        });
    }
}
