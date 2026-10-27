package cash.bchd.android_neutrino.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

import cash.bchd.android_neutrino.R;

/**
 * PinDotsView shows one dot per digit of a PIN, filled for the digits entered so far.
 */
public class PinDotsView extends LinearLayout {

    private final int emptyBackground;
    private final int filledBackground;
    private final int dotDiameter;
    private final int dotSpacing;

    public PinDotsView(Context context) {
        this(context, null);
    }

    public PinDotsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

        float density = getResources().getDisplayMetrics().density;
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PinDotsView);
        emptyBackground = a.getResourceId(R.styleable.PinDotsView_dotEmptyBackground, R.drawable.dot_empty);
        filledBackground = a.getResourceId(R.styleable.PinDotsView_dotFilledBackground, R.drawable.dot_filled);
        dotDiameter = a.getDimensionPixelSize(R.styleable.PinDotsView_dotDiameter, Math.round(16 * density));
        dotSpacing = a.getDimensionPixelSize(R.styleable.PinDotsView_dotSpacing, Math.round(16 * density));
        int pinLength = a.getInt(R.styleable.PinDotsView_pinLength, PinPadView.DEFAULT_PIN_LENGTH);
        a.recycle();

        setPinLength(pinLength);
    }

    /**
     * Show the given number of empty dots.
     */
    public void setPinLength(int pinLength) {
        removeAllViews();
        for (int i = 0; i < pinLength; i++) {
            View dot = new View(getContext());
            dot.setBackgroundResource(emptyBackground);
            LayoutParams lp = new LayoutParams(dotDiameter, dotDiameter);
            lp.setMargins(dotSpacing / 2, 0, dotSpacing / 2, 0);
            addView(dot, lp);
        }
    }

    /**
     * Fill the first count dots.
     */
    public void setFilled(int count) {
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).setBackgroundResource(i < count ? filledBackground : emptyBackground);
        }
    }
}
