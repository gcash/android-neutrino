package cash.bchd.android_neutrino.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;

import cash.bchd.android_neutrino.R;

/**
 * PinPadView is a numeric keypad for entering a PIN of a fixed length.
 */
public class PinPadView extends GridLayout {

    public static final int DEFAULT_PIN_LENGTH = 4;

    /**
     * Listener is told about the PIN as it is entered.
     */
    public interface Listener {
        /**
         * Called once all digits of the PIN have been entered.
         */
        void onComplete(String pin);

        /**
         * Called when the last digit has been deleted.
         */
        void onEmpty();

        /**
         * Called after each digit that is entered or deleted.
         */
        void onPinChange(int pinLength, String intermediatePin);
    }

    // The keys in the order they are laid out, three per row. The empty key
    // keeps 0 in the middle column and DELETE marks the delete key.
    private static final String DELETE = "DELETE";
    private static final String[] KEYS = {
            "1", "2", "3",
            "4", "5", "6",
            "7", "8", "9",
            "", "0", DELETE,
    };

    private final StringBuilder pin = new StringBuilder();
    private int pinLength;
    private Listener listener;
    private PinDotsView dots;

    public PinPadView(Context context) {
        this(context, null);
    }

    public PinPadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setColumnCount(3);

        float density = getResources().getDisplayMetrics().density;
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PinPadView);
        int textColor = a.getColor(R.styleable.PinPadView_keypadTextColor, Color.BLACK);
        float textSize = a.getDimension(R.styleable.PinPadView_keypadTextSize,
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 28, getResources().getDisplayMetrics()));
        int buttonSize = a.getDimensionPixelSize(R.styleable.PinPadView_keypadButtonSize, Math.round(72 * density));
        int horizontalSpacing = a.getDimensionPixelSize(R.styleable.PinPadView_keypadHorizontalSpacing, Math.round(40 * density));
        int verticalSpacing = a.getDimensionPixelSize(R.styleable.PinPadView_keypadVerticalSpacing, Math.round(16 * density));
        pinLength = a.getInt(R.styleable.PinPadView_pinLength, DEFAULT_PIN_LENGTH);
        a.recycle();

        TypedValue ripple = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true);

        for (int i = 0; i < KEYS.length; i++) {
            String key = KEYS[i];
            View view;
            if (key.equals(DELETE)) {
                ImageView delete = new ImageView(context);
                delete.setImageResource(R.drawable.ic_backspace);
                delete.setColorFilter(textColor);
                delete.setScaleType(ImageView.ScaleType.CENTER);
                delete.setContentDescription(context.getString(R.string.pin_delete));
                delete.setOnClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    deleteDigit();
                });
                view = delete;
            } else {
                TextView digit = new TextView(context);
                digit.setText(key);
                digit.setTextColor(textColor);
                digit.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
                digit.setGravity(Gravity.CENTER);
                if (key.isEmpty()) {
                    digit.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                } else {
                    digit.setOnClickListener(v -> {
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        addDigit(key);
                    });
                }
                view = digit;
            }
            if (!key.isEmpty()) {
                view.setBackgroundResource(ripple.resourceId);
            }

            int row = i / 3;
            int column = i % 3;
            LayoutParams lp = new LayoutParams(spec(row), spec(column));
            lp.width = buttonSize;
            lp.height = buttonSize;
            lp.setMargins(column == 0 ? 0 : horizontalSpacing, row == 0 ? 0 : verticalSpacing, 0, 0);
            addView(view, lp);
        }
    }

    public void setPinLockListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * Show the progress of the PIN entry on the dots.
     */
    public void attachIndicatorDots(PinDotsView dots) {
        this.dots = dots;
        dots.setPinLength(pinLength);
        dots.setFilled(pin.length());
    }

    /**
     * Clear the digits entered so far.
     */
    public void reset() {
        pin.setLength(0);
        if (dots != null) {
            dots.setFilled(0);
        }
    }

    private void addDigit(String digit) {
        if (pin.length() >= pinLength) {
            return;
        }
        pin.append(digit);
        changed();
        if (pin.length() == pinLength && listener != null) {
            listener.onComplete(pin.toString());
        }
    }

    private void deleteDigit() {
        if (pin.length() == 0) {
            return;
        }
        pin.setLength(pin.length() - 1);
        changed();
        if (pin.length() == 0 && listener != null) {
            listener.onEmpty();
        }
    }

    private void changed() {
        if (dots != null) {
            dots.setFilled(pin.length());
        }
        if (listener != null) {
            listener.onPinChange(pin.length(), pin.toString());
        }
    }
}
