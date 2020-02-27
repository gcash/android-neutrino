package cash.bchd.android_neutrino;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.fingerprint.FingerprintManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.ebanx.swipebtn.SwipeButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;

import cash.bchd.android_neutrino.wallet.Amount;
import cash.bchd.android_neutrino.wallet.Wallet;
import cdflynn.android.library.checkview.CheckView;
import io.grpc.Status;
import walletrpc.Api;

public class ConfirmationActivity extends FingerprintActivity {

    byte[] serializedTx;
    List<Long> inputVals;
    String paymentAddr;
    String memo;
    SwipeButton swipeButton;
    EncryptionType encType;

    boolean isPaymentRequest;
    byte[] merchantData;
    String paymentURL;
    String refundAddress;
    long refundAmount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_confirmation);

        CloseActivity.cancelCloseTimer();

        Intent intent = getIntent();
        encType = Settings.getInstance().getEncryptionType();
        swipeButton = findViewById(R.id.swipe_btn);
        if (encType == EncryptionType.PIN) {
            swipeButton.setVisibility(View.VISIBLE);
        } else if (encType == EncryptionType.FINGERPRINT) {
            ImageView fingerprint = findViewById(R.id.fingerprint);
            fingerprint.setVisibility(View.VISIBLE);
        } else {
            swipeButton.setVisibility(View.VISIBLE);
        }

        paymentAddr = intent.getStringExtra("paymentAddress");
        String bchAmount = intent.getStringExtra("amountBCH");
        String formattedFiat = intent.getStringExtra("amountFiat");
        long txFee = intent.getLongExtra("fee", 0);
        serializedTx = intent.getByteArrayExtra("serializedTransaction");
        ArrayList<String> inputStrings = intent.getStringArrayListExtra("inputVals");

        // Extra data if payment request
        isPaymentRequest = intent.getBooleanExtra("isPaymentRequest", false);
        merchantData = intent.getByteArrayExtra("merchantData");
        paymentURL = intent.getStringExtra("paymentURL");
        refundAddress = intent.getStringExtra("refundAddress");
        refundAmount = intent.getLongExtra("refundAmount", 0);

        inputVals = new ArrayList<>();
        for (String s : inputStrings) {
            inputVals.add(Long.valueOf(s));
        }

        memo = intent.getStringExtra("memo");
        String label = intent.getStringExtra("label");

        TextView amountTxtView = findViewById(R.id.confirmBchAmount);
        amountTxtView.setText(getString(R.string.bch_amount, bchAmount));

        TextView fiatAmountTxtView = findViewById(R.id.confirmFiatAmount);
        fiatAmountTxtView.setText(formattedFiat);

        TextView payTo = findViewById(R.id.payTo);
        if (label != null && !label.equals("")) {
            payTo.setText(label);
        } else {
            TextView payToLabel = findViewById(R.id.payToLabel);
            View div7 = findViewById(R.id.divider7);
            payTo.setVisibility(View.GONE);
            payToLabel.setVisibility(View.GONE);
            div7.setVisibility(View.GONE);
            TextView paymentAddrLabel = findViewById(R.id.paymentAddrLabel);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) paymentAddrLabel.getLayoutParams();
            params.setMargins(params.leftMargin, 30, params.rightMargin, params.bottomMargin);
            paymentAddrLabel.setLayoutParams(params);
        }
        if (isPaymentRequest) {
            payTo.setCompoundDrawablesWithIntrinsicBounds(R.drawable.small_shield, 0, 0, 0);
        }

        TextView paymentAddress = findViewById(R.id.paymentAddress);
        paymentAddress.setText(paymentAddr);

        TextView networkFee = findViewById(R.id.networkFee);
        networkFee.setText(getString(R.string.bch_amount, new Amount(txFee).toString()));

        TextView memoTxtView = findViewById(R.id.memoConfirmation);
        if (memo != null && !memo.equals("")) {
            memoTxtView.setText(memo);
        } else {
            TextView memoLabel = findViewById(R.id.memoLabel);
            memoTxtView.setVisibility(View.GONE);
            memoLabel.setVisibility(View.GONE);
        }


        swipeButton.setOnStateChangeListener(active -> {
            if (active) {
                if (encType == EncryptionType.PIN) {
                    Settings settings = Settings.getInstance();
                    if (settings.getInvalidPinCount() >= 3 && settings.getLastInvalidPin() + 300000 > System.currentTimeMillis()) {
                        CoordinatorLayout layout = findViewById(R.id.confirmationMainLayout);
                        Snackbar snackbar = Snackbar.make(layout, "Too many invalid pin attempts. Wait five minutes.", Snackbar.LENGTH_LONG);
                        snackbar.show();
                        return;
                    }

                    Intent intent1 = new Intent(ConfirmationActivity.this, PinActivity.class);
                    intent1.putExtra("enterOnly", true);
                    startActivityForResult(intent1, 1234);
                    return;
                }
                signTransaction(serializedTx, paymentAddr, memo, inputVals, Wallet.DEFAULT_PASSPHRASE);
            }
        });

        if (encType == EncryptionType.FINGERPRINT) {
            CoordinatorLayout layout = findViewById(R.id.confirmationMainLayout);
            try {
                this.initFingerprintScanner(false);
                FingerprintHandler handler = new FingerprintHandler(this) {
                    @Override
                    public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
                        try {
                            String encryptedPwHex = Settings.getInstance().getEncryptedPassword();

                            byte[] encrypted = BaseEncoding.base16().lowerCase().decode(encryptedPwHex);

                            byte[] decrypted = result.getCryptoObject().getCipher().doFinal(encrypted);

                            String password = BaseEncoding.base16().lowerCase().encode(decrypted);

                            signTransaction(serializedTx, paymentAddr, memo, inputVals, password);
                        } catch (Exception e) {
                            Snackbar snackbar = Snackbar.make(layout, e.getMessage(), Snackbar.LENGTH_LONG);
                            snackbar.show();
                        }
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        Snackbar snackbar = Snackbar.make(layout, "Invalid fingerprint", Snackbar.LENGTH_LONG);
                        snackbar.show();
                    }
                };
                handler.startAuth(fingerprintManager, cryptoObject);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        if (Integer.parseInt(android.os.Build.VERSION.SDK) > 5
                && keyCode == KeyEvent.KEYCODE_BACK
                && event.getRepeatCount() == 0) {
            onBackPressed();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }


    @Override
    public void onBackPressed() {
        LinearLayout checkLayout = findViewById(R.id.checkLayout);
        if (checkLayout.getVisibility() == View.VISIBLE) {
            finish();
            if (SendActivity.fa != null) {
                SendActivity.fa.finish();
            }
            if (UriActivity.fa != null) {
                UriActivity.fa.finish();
            }
        } else {
            super.onBackPressed();
        }
    }

    private void signTransaction(byte[] serializedTx, String paymentAddr, String memo, List<Long> inputVals, String password) {
        Wallet wallet = Wallet.getInstance();
        CoordinatorLayout layout = findViewById(R.id.confirmationMainLayout);
        ListenableFuture<Api.SignTransactionResponse> res = wallet.signTransactionAsync(serializedTx, inputVals, password);
        Futures.addCallback(res, new FutureCallback<Api.SignTransactionResponse>() {
            @Override
            public void onSuccess(Api.SignTransactionResponse result) {
                Settings.getInstance().setInvalidPinCount(0);
                if (result.getUnsignedInputIndexesList().size() > 0) {
                    Snackbar snackbar = Snackbar.make(layout, "Failed to sign transaction", Snackbar.LENGTH_LONG);
                    snackbar.show();
                    return;
                }

                if (!isPaymentRequest) {
                    publishTransaction(wallet, result, layout);
                } else {
                    postPaymentToMerchant(wallet, result, layout);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                runOnUiThread(() -> {
                    Status status = Status.fromThrowable(t);
                    String errStr = status.getDescription();
                    if (status.getDescription() != null && status.getDescription().equals("invalid passphrase for master private key")) {
                        errStr = "Invalid Pin";
                        if (encType == EncryptionType.FINGERPRINT) {
                            errStr = "Invalid Fingerprint";
                        } else if (encType == EncryptionType.PIN) {
                            Settings settings = Settings.getInstance();
                            int invalidCount = settings.getInvalidPinCount();
                            invalidCount++;
                            settings.setInvalidPinCount(invalidCount);
                            settings.setLastInvalidPin(System.currentTimeMillis());
                        }
                    }
                    Snackbar snackbar = Snackbar.make(layout, errStr, Snackbar.LENGTH_LONG);
                    snackbar.show();
                    swipeButton.setHasActivationState(false);
                });
            }
        });
    }

    private void publishTransaction(Wallet wallet, Api.SignTransactionResponse result, CoordinatorLayout layout) {
        ListenableFuture<Api.PublishTransactionResponse> resp = wallet.publishTransactionAsync(result.getTransaction().toByteArray(), paymentAddr, memo);
        Futures.addCallback(resp, new FutureCallback<Api.PublishTransactionResponse>() {
            @Override
            public void onSuccess(Api.PublishTransactionResponse result) {
                runOnUiThread(() -> {
                    RelativeLayout rLayout = findViewById(R.id.confirmationLayout);
                    CheckView checkView = findViewById(R.id.check);
                    LinearLayout checkLayout = findViewById(R.id.checkLayout);
                    checkLayout.setOnClickListener(v -> onBackPressed());
                    rLayout.setVisibility(View.GONE);
                    checkLayout.setVisibility(View.VISIBLE);
                    checkView.check();
                    Vibrator vibrator = (Vibrator) getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
                    if (vibrator != null) {
                        vibrator.vibrate(500);
                    }
                });
            }

            @Override
            public void onFailure(Throwable t) {
                runOnUiThread(() -> {
                    Status status = Status.fromThrowable(t);
                    if (status.getDescription() != null) {
                        Snackbar snackbar = Snackbar.make(layout, status.getDescription(), Snackbar.LENGTH_LONG);
                        snackbar.show();
                    }
                });
            }
        });
    }

    private void postPaymentToMerchant(Wallet wallet, Api.SignTransactionResponse result, CoordinatorLayout layout) {
        ListenableFuture<Api.PostPaymentResponse> resp = wallet.postPaymentAsync(refundAddress, refundAmount, result.getTransaction().toByteArray(), paymentURL, merchantData, paymentAddr, memo);
        Futures.addCallback(resp, new FutureCallback<Api.PostPaymentResponse>() {
            @Override
            public void onSuccess(Api.PostPaymentResponse result) {
                runOnUiThread(() -> {
                    RelativeLayout rLayout = findViewById(R.id.confirmationLayout);
                    CheckView checkView = findViewById(R.id.check);
                    LinearLayout checkLayout = findViewById(R.id.checkLayout);
                    checkLayout.setOnClickListener(v -> onBackPressed());

                    rLayout.setVisibility(View.GONE);
                    checkLayout.setVisibility(View.VISIBLE);
                    checkView.check();
                    Vibrator vibrator = (Vibrator) getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
                    vibrator.vibrate(500);
                });
            }

            @Override
            public void onFailure(Throwable t) {
                runOnUiThread(() -> {
                    Status status = Status.fromThrowable(t);
                    if (status.getDescription() != null) {
                        Snackbar snackbar = Snackbar.make(layout, status.getDescription(), Snackbar.LENGTH_LONG);
                        snackbar.show();
                    }
                });
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode) {
            case (1234) : {
                if (resultCode == Activity.RESULT_OK) {
                    String pin = data.getStringExtra("pin");
                    String pw = "";
                    try {
                        pw = Wallet.SHA256(pin);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    signTransaction(serializedTx, paymentAddr, memo, inputVals, pw);
                }
                break;
            }
        }
    }
}