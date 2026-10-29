package cash.bchd.android_neutrino;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/**
 * ForwardSwipeFrame holds a pager that the user can swipe only forward, as the backup's quiz
 * mustn't go back to the words. It cancels a gesture that goes back past where it started, from
 * the start or after dragging forward, and leaves a vertical one, as a page's scrolling, alone.
 */
public class ForwardSwipeFrame extends FrameLayout {

    private final int touchSlop;
    private float startX;
    private float startY;
    // Whether the gesture went far enough to tell whether it's horizontal.
    private boolean decided;
    private boolean horizontal;
    // Whether the gesture went back, after which the frame swallows the rest of it.
    private boolean cancelled;

    public ForwardSwipeFrame(Context context, AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startX = event.getX();
                startY = event.getY();
                decided = false;
                horizontal = false;
                cancelled = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (cancelled) {
                    return true;
                }
                float dx = event.getX() - startX;
                float dy = event.getY() - startY;
                if (!decided && Math.max(Math.abs(dx), Math.abs(dy)) > touchSlop) {
                    decided = true;
                    horizontal = Math.abs(dx) > Math.abs(dy);
                }
                float back = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? -dx : dx;
                if (horizontal && back > touchSlop) {
                    cancelled = true;
                    MotionEvent cancel = MotionEvent.obtain(event);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(cancel);
                    cancel.recycle();
                    return true;
                }
                break;
            default:
                if (cancelled) {
                    return true;
                }
        }
        return super.dispatchTouchEvent(event);
    }
}
