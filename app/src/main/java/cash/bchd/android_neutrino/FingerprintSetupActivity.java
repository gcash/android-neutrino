package cash.bchd.android_neutrino;

import android.content.Context;
import android.content.Intent;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.security.SecureRandom;
import cash.bchd.android_neutrino.wallet.Wallet;
import io.grpc.Status;
import walletrpc.Api;

public class FingerprintSetupActivity extends FingerprintActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fingerprint);

        Intent intent = getIntent();
        boolean removeFingerprint = intent.getBooleanExtra("removeFingerprint", false);

        if (removeFingerprint) {
            try {
                TextView info = findViewById(R.id.fingerprintDesc);
                info.setText("Scan your fingerprint to remove encryption and return to an unencrypted state.");
                this.initFingerprintScanner(false);
                FingerprintHandler handler = new FingerprintHandler(this) {
                    @Override
                    public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
                        Settings settings = Settings.getInstance();
                        RelativeLayout layout = findViewById(R.id.fingerprintSetupLayout);
                        try {

                            Wallet wallet = Wallet.getInstance();

                            String encryptedPwHex = Settings.getInstance().getEncryptedPassword();

                            byte[] encrypted = BaseEncoding.base16().lowerCase().decode(encryptedPwHex);

                            byte[] decrypted = result.getCryptoObject().getCipher().doFinal(encrypted);

                            String password = BaseEncoding.base16().lowerCase().encode(decrypted);

                            System.out.println(password);

                            ListenableFuture<Api.ChangePassphraseResponse> res = wallet.changePasswordAsync(password, Wallet.DEFAULT_PASSPHRASE);
                            Futures.addCallback(res, new FutureCallback<Api.ChangePassphraseResponse>() {
                                @Override
                                public void onSuccess(Api.ChangePassphraseResponse result) {
                                    Vibrator vibrator = (Vibrator) getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
                                    vibrator.vibrate(500);
                                    settings.setEncryptionType(EncryptionType.UNENCRYPTED);
                                    SettingsActivity.fa.finish();
                                    finish();
                                }

                                @Override
                                public void onFailure(Throwable t) {
                                    runOnUiThread(new Runnable() {

                                        @Override
                                        public void run() {
                                            Status status = Status.fromThrowable(t);
                                            Snackbar snackbar = Snackbar.make(layout, status.getDescription(), Snackbar.LENGTH_LONG);
                                            snackbar.show();
                                        }
                                    });
                                }
                            });
                        } catch (Exception e) {
                            runOnUiThread(new Runnable() {

                                @Override
                                public void run() {
                                    Snackbar snackbar = Snackbar.make(layout, e.getMessage(), Snackbar.LENGTH_LONG);
                                    snackbar.show();
                                }
                            });
                        }
                    }
                };
                handler.startAuth(fingerprintManager, cryptoObject);
            } catch (Exception e) {
                TextView desc = findViewById(R.id.fingerprintDesc);
                desc.setText(e.getMessage().substring(e.getMessage().indexOf(":") + 2));
            }
            return;
        }

        try {
            this.initFingerprintScanner(true);
            FingerprintHandler handler = new FingerprintHandler(this) {
                @Override
                public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
                    Settings settings = Settings.getInstance();
                    RelativeLayout layout = (RelativeLayout) findViewById(R.id.fingerprintSetupLayout);
                    try {
                        SecureRandom random = new SecureRandom();
                        byte[] pwBytes = new byte[32];
                        random.nextBytes(pwBytes);

                        byte[] encrypted = result.getCryptoObject().getCipher().doFinal(pwBytes);
                        Wallet wallet = Wallet.getInstance();

                        String pwHex = BaseEncoding.base16().lowerCase().encode(pwBytes);
                        ListenableFuture<Api.ChangePassphraseResponse> res = wallet.changePasswordAsync(Wallet.DEFAULT_PASSPHRASE, pwHex);
                        Futures.addCallback(res, new FutureCallback<Api.ChangePassphraseResponse>() {
                            @Override
                            public void onSuccess(Api.ChangePassphraseResponse result) {
                                Vibrator vibrator = (Vibrator) getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
                                vibrator.vibrate(500);
                                settings.setEncryptedPassword(BaseEncoding.base16().lowerCase().encode(encrypted));
                                settings.setEncryptionType(EncryptionType.FINGERPRINT);
                                SettingsActivity.fa.finish();
                                finish();
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                runOnUiThread(new Runnable() {

                                    @Override
                                    public void run() {
                                        Status status = Status.fromThrowable(t);
                                        Snackbar snackbar = Snackbar.make(layout, status.getDescription(), Snackbar.LENGTH_LONG);
                                        snackbar.show();
                                    }
                                });
                            }
                        });
                    } catch (Exception e) {
                        runOnUiThread(new Runnable() {

                            @Override
                            public void run() {
                                Snackbar snackbar = Snackbar.make(layout, e.getMessage(), Snackbar.LENGTH_LONG);
                                snackbar.show();
                            }
                        });
                    }
                }
            };
            handler.startAuth(fingerprintManager, cryptoObject);
        } catch (Exception e) {
            TextView desc = (TextView) findViewById(R.id.fingerprintDesc);
            desc.setText(e.getMessage().substring(e.getMessage().indexOf(":") + 2));
        }
    }
}
