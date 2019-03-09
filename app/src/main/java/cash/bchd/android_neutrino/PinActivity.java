package cash.bchd.android_neutrino;

import android.content.Context;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.andrognito.pinlockview.IndicatorDots;
import com.andrognito.pinlockview.PinLockListener;
import com.andrognito.pinlockview.PinLockView;

import cash.bchd.android_neutrino.wallet.Wallet;

public class PinActivity extends AppCompatActivity {
    private boolean firstPinEntered = false;
    String firstPin;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin);

        IndicatorDots mIndicatorDots = (IndicatorDots) findViewById(R.id.pinIndicatorDots);
        PinLockView mPinLockView = (PinLockView) findViewById(R.id.pinpad);
        mPinLockView.setPinLockListener(new PinLockListener() {
            @Override
            public void onComplete(String pin) {
                if (!firstPinEntered) {
                    firstPin = pin;
                    firstPinEntered = true;

                    TextView pinLabel = (TextView) findViewById(R.id.pinLabel);
                    pinLabel.setText("Confirm Pin");
                    mPinLockView.resetPinLockView();
                } else {
                    if (!pin.equals(firstPin)) {
                        TextView invalid = (TextView) findViewById(R.id.invalidPin);
                        invalid.setVisibility(View.VISIBLE);
                        mPinLockView.resetPinLockView();
                        Vibrator vibrator = (Vibrator)getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
                        vibrator.vibrate(500);
                    } else {
                        Thread thread = new Thread() {
                            public void run() {
                                try {
                                    Wallet wallet = Wallet.getInstance();
                                    String hashedPw = Wallet.SHA256(pin);
                                    wallet.changePassword(Wallet.DEFAULT_PASSPHRASE, hashedPw);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        };
                        Settings.getInstance().setEncryptionType(EncryptionType.PIN);
                        Vibrator vibrator = (Vibrator)getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
                        vibrator.vibrate(500);
                        LinearLayout layout = (LinearLayout) findViewById(R.id.setPinLayout);
                        Snackbar snackbar = Snackbar.make(layout, "New pin set.", Snackbar.LENGTH_LONG);
                        snackbar.show();
                        SettingsActivity.fa.finish();
                        finish();
                    }
                }
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
