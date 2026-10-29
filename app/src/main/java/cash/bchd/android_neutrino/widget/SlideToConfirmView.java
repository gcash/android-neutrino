package cash.bchd.android_neutrino.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import cash.bchd.android_neutrino.R;

/**
 * SlideToConfirmView asks the user to slide its thumb to the end of the track to confirm an
 * action, such as sending a payment. It confirms once: further slides are ignored until reset
 * is called, so a payment can't be sent twice by sliding again while it is being sent.
 *
 * With touch exploration on, such as with TalkBack, a double tap confirms.
 */
public class SlideToConfirmView extends FrameLayout {

    /**
     * OnConfirmListener is called when the user has slid the thumb to the end.
     */
    public interface OnConfirmListener {
        void onConfirm();
    }

    // The fraction of the track the thumb has to be slid across to confirm.
    private static final float CONFIRM_THRESHOLD = 0.85f;
    private static final long ANIMATION_MILLIS = 150;

    private final TextView label;
    private final ImageView thumb;
    private OnConfirmListener listener;
    private boolean confirmed;
    private float downX;
    private float downTranslation;
    private boolean dragging;

    public SlideToConfirmView(Context context) {
        this(context, null);
    }

    public SlideToConfirmView(Context context, AttributeSet attrs) {
        super(context, attrs);

        float density = getResources().getDisplayMetrics().density;
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SlideToConfirmView);
        CharSequence text = a.getText(R.styleable.SlideToConfirmView_slideText);
        int trackBackground = a.getResourceId(R.styleable.SlideToConfirmView_slideTrackBackground, R.drawable.shape_rounded);
        int thumbBackground = a.getResourceId(R.styleable.SlideToConfirmView_slideThumbBackground, R.drawable.shape_button);
        int thumbIcon = a.getResourceId(R.styleable.SlideToConfirmView_slideThumbIcon, 0);
        int thumbWidth = a.getDimensionPixelSize(R.styleable.SlideToConfirmView_slideThumbWidth, Math.round(80 * density));
        int thumbPadding = a.getDimensionPixelSize(R.styleable.SlideToConfirmView_slideThumbPadding, Math.round(12 * density));
        a.recycle();

        setBackgroundResource(trackBackground);
        setMinimumHeight(Math.round(64 * density));
        setClickable(true);
        setFocusable(true);
        setContentDescription(text);

        label = new TextView(context);
        label.setText(text);
        label.setTextColor(Color.WHITE);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        label.setLetterSpacing(0.1f);
        label.setGravity(Gravity.CENTER);
        label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(label, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));

        thumb = new ImageView(context);
        thumb.setBackgroundResource(thumbBackground);
        if (thumbIcon != 0) {
            thumb.setImageResource(thumbIcon);
        }
        thumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
        thumb.setPadding(thumbPadding, thumbPadding, thumbPadding, thumbPadding);
        thumb.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(thumb, new LayoutParams(thumbWidth, LayoutParams.MATCH_PARENT, Gravity.START | Gravity.CENTER_VERTICAL));
    }

    public void setOnConfirmListener(OnConfirmListener listener) {
        this.listener = listener;
    }

    /**
     * Whether the user has confirmed since the view was created or last reset.
     */
    public boolean isConfirmed() {
        return confirmed;
    }

    /**
     * Slide the thumb back to the start so that the user can confirm again.
     */
    public void reset() {
        confirmed = false;
        dragging = false;
        animateThumb(0, false);
    }

    /**
     * Show the thumb at the end, as once the user confirmed, without telling the listener, for
     * a recreated screen whose confirmed action goes on.
     */
    public void showConfirmed() {
        confirmed = true;
        dragging = false;
        // The end is known once the view is laid out.
        post(() -> {
            thumb.setTranslationX(maxTranslation());
            label.setAlpha(0);
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled() || confirmed) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!onThumb(event.getX())) {
                    return false;
                }
                dragging = true;
                downX = event.getX();
                downTranslation = thumb.getTranslationX();
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    float translation = Math.max(0, Math.min(maxTranslation(), downTranslation + event.getX() - downX));
                    thumb.setTranslationX(translation);
                    label.setAlpha(1 - translation / Math.max(1, maxTranslation()));
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    dragging = false;
                    boolean confirm = thumb.getTranslationX() >= maxTranslation() * CONFIRM_THRESHOLD;
                    animateThumb(confirm ? maxTranslation() : 0, confirm);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    dragging = false;
                    animateThumb(0, false);
                }
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        AccessibilityManager am = (AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am != null && am.isTouchExplorationEnabled() && isEnabled() && !confirmed) {
            animateThumb(maxTranslation(), true);
        }
        return true;
    }

    private boolean onThumb(float x) {
        float left = thumb.getLeft() + thumb.getTranslationX();
        return x >= left && x <= left + thumb.getWidth();
    }

    private float maxTranslation() {
        return getWidth() - getPaddingLeft() - getPaddingRight() - thumb.getWidth();
    }

    private void animateThumb(float translation, boolean confirm) {
        if (confirm) {
            // Latch before the animation ends so that the slide can't be
            // confirmed twice.
            confirmed = true;
        }
        thumb.animate().translationX(translation).setDuration(ANIMATION_MILLIS).withEndAction(() -> {
            if (confirm) {
                performHapticFeedback(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                        ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.LONG_PRESS);
                if (listener != null) {
                    listener.onConfirm();
                }
            }
        }).start();
        label.animate().alpha(translation == 0 ? 1 : 0).setDuration(ANIMATION_MILLIS).start();
    }
}
