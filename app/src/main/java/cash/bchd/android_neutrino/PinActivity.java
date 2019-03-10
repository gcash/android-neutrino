package cash.bchd.android_neutrino;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import cash.bchd.android_neutrino.wallet.Wallet;
import io.grpc.Status;
import walletrpc.Api;

public class PinActivity extends AppCompatActivity {
    private boolean firstPinEntered = false;
    String firstPin;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin);

        Intent intent = getIntent();
        boolean enterOnly = intent.getBooleanExtra("enterOnly", false);
        boolean removePin = intent.getBooleanExtra("removePin", false);

        if (enterOnly || removePin) {
            TextView pinLabel = (TextView) findViewById(R.id.pinLabel);
            pinLabel.setText("Enter Pin");
        }


        IndicatorDots mIndicatorDots = (IndicatorDots) findViewById(R.id.pinIndicatorDots);
        PinLockView mPinLockView = (PinLockView) findViewById(R.id.pinpad);
        mPinLockView.setPinLockListener(new PinLockListener() {
            @Override
            public void onComplete(String pin) {
                if (removePin) {
                    LinearLayout mCLayout = (LinearLayout) findViewById(R.id.setPinLayout);
                    try {
                        Wallet wallet = Wallet.getInstance();
                        String hashedPw = Wallet.SHA256(pin);
                        ListenableFuture<Api.ChangePassphraseResponse> res = wallet.changePasswordAsync(hashedPw, Wallet.DEFAULT_PASSPHRASE);
                        Futures.addCallback(res, new FutureCallback<Api.ChangePassphraseResponse>() {
                            @Override
                            public void onSuccess(Api.ChangePassphraseResponse result) {
                                Settings.getInstance().setEncryptionType(EncryptionType.UNENCRYPTED);
                                Vibrator vibrator = (Vibrator) getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
                                vibrator.vibrate(500);
                                SettingsActivity.fa.finish();
                                finish();
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                runOnUiThread(new Runnable() {

                                    @Override
                                    public void run() {
                                        Status status = Status.fromThrowable(t);
                                        Snackbar snackbar = Snackbar.make(mCLayout, status.getDescription(), Snackbar.LENGTH_LONG);
                                        snackbar.show();
                                        mPinLockView.resetPinLockView();
                                    }
                                });
                            }
                        });
                    } catch (Exception e) {
                        Snackbar snackbar = Snackbar.make(mCLayout, "Error removing pin", Snackbar.LENGTH_LONG);
                        snackbar.show();
                        mPinLockView.resetPinLockView();
                        e.printStackTrace();
                    }
                    return;
                }

                if (enterOnly) {
                    Intent output = new Intent();
                    output.putExtra("pin", pin);
                    setResult(RESULT_OK, output);
                    finish();
                    return;
                }

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
                        LinearLayout mCLayout = (LinearLayout) findViewById(R.id.setPinLayout);
                        try {
                            Wallet wallet = Wallet.getInstance();
                            String hashedPw = Wallet.SHA256(pin);
                            ListenableFuture<Api.ChangePassphraseResponse> res = wallet.changePasswordAsync(Wallet.DEFAULT_PASSPHRASE, hashedPw);
                            Futures.addCallback(res, new FutureCallback<Api.ChangePassphraseResponse>() {
                                @Override
                                public void onSuccess(Api.ChangePassphraseResponse result) {
                                    Settings.getInstance().setEncryptionType(EncryptionType.PIN);
                                    Vibrator vibrator = (Vibrator) getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
                                    vibrator.vibrate(500);
                                    SettingsActivity.fa.finish();
                                    finish();
                                }

                                @Override
                                public void onFailure(Throwable t) {
                                    runOnUiThread(new Runnable() {

                                        @Override
                                        public void run() {
                                            Status status = Status.fromThrowable(t);
                                            Snackbar snackbar = Snackbar.make(mCLayout, status.getDescription(), Snackbar.LENGTH_LONG);
                                            snackbar.show();
                                        }
                                    });
                                }
                            });
                        } catch (Exception e) {
                            Snackbar snackbar = Snackbar.make(mCLayout, "Error setting password", Snackbar.LENGTH_LONG);
                            snackbar.show();
                            e.printStackTrace();
                        }
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
