package cash.bchd.android_neutrino;

import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import cash.bchd.android_neutrino.widget.PinDotsView;
import cash.bchd.android_neutrino.widget.PinPadView;

/**
 * PinActivity asks for the PIN that earlier versions protected the wallet with, so that the wallet
 * can move to the screen lock, and returns it to the activity that started it.
 */
public class PinActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin);
        SystemBars.apply(this);
        // Keep the PIN out of screenshots and the recent apps.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        PinDotsView mIndicatorDots = findViewById(R.id.pinIndicatorDots);
        PinPadView mPinLockView = findViewById(R.id.pinpad);
        mPinLockView.setPinLockListener(new PinPadView.Listener() {
            @Override
            public void onComplete(String pin) {
                Intent output = new Intent();
                output.putExtra("pin", pin);
                setResult(RESULT_OK, output);
                finish();
            }

            @Override
            public void onEmpty() {
            }

            @Override
            public void onPinChange(int pinLength, String intermediatePin) {
            }
        });
        mPinLockView.attachIndicatorDots(mIndicatorDots);
    }
}
