package cash.bchd.android_neutrino;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import cash.bchd.android_neutrino.wallet.Amount;
import cash.bchd.android_neutrino.wallet.Wallet;
import cash.bchd.android_neutrino.wallet.WalletSecret;
import cash.bchd.android_neutrino.widget.CheckView;
import cash.bchd.android_neutrino.widget.SlideToConfirmView;
import io.grpc.Status;
import walletrpc.Api;

public class ConfirmationActivity extends AppCompatActivity {


    byte[] serializedTx;
    List<Long> inputVals;
    String paymentAddr;
    String memo;
    SlideToConfirmView swipeButton;
    ScreenLock.Prompt screenLock;
    // The PIN of earlier versions, which unlocks the wallet to sign the payment.
    private final ActivityResultLauncher<Void> enterPin =
            registerForActivityResult(new PinActivity.EnterPin(), pin -> {
                if (pin == null) {
                    swipeButton.reset();
                    return;
                }
                String pw = "";
                try {
                    pw = Wallet.SHA256(pin);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                upgradeAndSign(pw);
            });
    Payment payment;

    /**
     * Payment keeps the screen's payment while it is sent and once it was, through a recreation
     * of the screen, as for a configuration change, so that the recreated screen shows it as
     * sending, sent or failed, rather than as a payment to swipe for.
     */
    public static class Payment extends ViewModel {
        // Set while the payment is signed and sent, and after it was sent.
        final AtomicBoolean sending = new AtomicBoolean(false);
        // The memo of the merchant's acknowledgement once the payment was sent, or "".
        final MutableLiveData<String> sent = new MutableLiveData<>();
        // Why the payment wasn't sent or accepted, until the screen showed it.
        final MutableLiveData<Failure> failure = new MutableLiveData<>();
        // Whether the screen showed the payment as sent, which it marks once with a vibration.
        boolean shownSent;
    }

    // Failure is why a payment wasn't sent or accepted, and whether it may be sent again.
    static final class Failure {
        final String message;
        final boolean retry;

        Failure(String message, boolean retry) {
            this.message = message;
            this.retry = retry;
        }
    }

    // Enabled once the payment is sent, to leave the send screen on back.
    OnBackPressedCallback afterPayment = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            leaveAfterPayment();
        }
    };

    boolean isPaymentRequest;
    int paymentProtocol;
    byte[] merchantData;
    String paymentURL;
    String refundAddress;
    long refundAmount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_confirmation);
        SystemBars.apply(this);
        getOnBackPressedDispatcher().addCallback(this, afterPayment);

        CloseActivity.cancelCloseTimer();
        Networks.showBadge(this, Settings.get(this).getNetwork());

        Intent intent = getIntent();
        swipeButton = findViewById(R.id.swipe_btn);
        screenLock = ScreenLock.prompt(this, new ScreenLock.Callback() {
            @Override
            public void onConfirmed() {
                signWithScreenLock();
            }

            @Override
            public void onCancelled() {
                swipeButton.reset();
            }
        });
        if (!ScreenLock.isSet(this)) {
            findViewById(R.id.noScreenLockNotice).setVisibility(View.VISIBLE);
        }

        paymentAddr = intent.getStringExtra("paymentAddress");
        String bchAmount = intent.getStringExtra("amountBCH");
        String formattedFiat = intent.getStringExtra("amountFiat");
        long txFee = intent.getLongExtra("fee", 0);
        serializedTx = intent.getByteArrayExtra("serializedTransaction");
        ArrayList<String> inputStrings = intent.getStringArrayListExtra("inputVals");

        // Extra data if payment request
        isPaymentRequest = intent.getBooleanExtra("isPaymentRequest", false);
        paymentProtocol = intent.getIntExtra("paymentProtocol", Api.PaymentProtocol.BIP70_VALUE);
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


        swipeButton.setOnConfirmListener(this::unlockAndSign);

        payment = new ViewModelProvider(this).get(Payment.class);
        if (payment.sending.get()) {
            swipeButton.showConfirmed();
        }
        payment.sent.observe(this, this::showPaymentSent);
        payment.failure.observe(this, failure -> {
            if (failure == null) {
                return;
            }
            payment.failure.setValue(null);
            if (failure.message != null) {
                CoordinatorLayout layout = findViewById(R.id.confirmationMainLayout);
                Snackbar snackbar = Snackbar.make(layout, failure.message, Snackbar.LENGTH_LONG);
                snackbar.setTextMaxLines(5);
                snackbar.show();
            }
            if (failure.retry) {
                swipeButton.reset();
            }
        });
    }

    // unlockAndSign gets the wallet's private passphrase the way the wallet keeps it: after the
    // screen lock, or from the PIN or fingerprint of earlier versions, which it then replaces.
    private void unlockAndSign() {
        Settings settings = Settings.getInstance();
        CoordinatorLayout layout = findViewById(R.id.confirmationMainLayout);
        switch (settings.getEncryptionType()) {
            case PIN:
                if (settings.pinLockedOut()) {
                    Snackbar snackbar = Snackbar.make(layout, R.string.pin_locked_out, Snackbar.LENGTH_LONG);
                    snackbar.show();
                    swipeButton.reset();
                    return;
                }
                enterPin.launch(null);
                return;
            case FINGERPRINT:
                LegacyFingerprint.read(this, settings, new LegacyFingerprint.Callback() {
                    @Override
                    public void onPassphrase(String passphrase) {
                        upgradeAndSign(passphrase);
                    }

                    @Override
                    public void onFailed(CharSequence message) {
                        Snackbar snackbar = Snackbar.make(layout, message, Snackbar.LENGTH_LONG);
                        snackbar.setTextMaxLines(5);
                        snackbar.show();
                        swipeButton.reset();
                    }
                });
                return;
            default:
                screenLock.confirm(getString(R.string.screen_lock_confirm_payment));
        }
    }

    // signWithScreenLock signs with the wallet's passphrase once the screen lock was confirmed,
    // which a sealed passphrase needs to be opened.
    private void signWithScreenLock() {
        Settings settings = Settings.getInstance();
        String passphrase = Wallet.DEFAULT_PASSPHRASE;
        if (settings.getEncryptionType() == EncryptionType.KEYSTORE) {
            try {
                passphrase = WalletSecret.open(settings.getSealedPassphrase());
            } catch (Exception e) {
                e.printStackTrace();
                CoordinatorLayout layout = findViewById(R.id.confirmationMainLayout);
                Snackbar snackbar = Snackbar.make(layout, R.string.wallet_key_missing, Snackbar.LENGTH_LONG);
                snackbar.setTextMaxLines(5);
                snackbar.show();
                swipeButton.reset();
                return;
            }
        }
        signTransaction(serializedTx, paymentAddr, memo, inputVals, passphrase);
    }

    // upgradeAndSign replaces the passphrase of an earlier version with a sealed one, if the phone
    // has a screen lock to ask for from now on, and signs with whichever the wallet then has. A
    // wrong PIN fails the upgrade, and signing with it counts the failed attempt.
    private void upgradeAndSign(String oldPassphrase) {
        if (!ScreenLock.isSet(this)) {
            signTransaction(serializedTx, paymentAddr, memo, inputVals, oldPassphrase);
            return;
        }
        PassphraseUpgrade.run(Wallet.getInstance(), Settings.getInstance(), oldPassphrase,
                ContextCompat.getMainExecutor(this), new PassphraseUpgrade.Callback() {
                    @Override
                    public void onUpgraded(String passphrase) {
                        signTransaction(serializedTx, paymentAddr, memo, inputVals, passphrase);
                    }

                    @Override
                    public void onFailed(Throwable t) {
                        signTransaction(serializedTx, paymentAddr, memo, inputVals, oldPassphrase);
                    }
                });
    }

    // Once the payment is sent, leave the send screen too, which closes on this result, so that
    // going back does not show the filled in payment again.
    private void leaveAfterPayment() {
        setResult(RESULT_OK);
        finish();
    }

    // showPaymentSent shows that the payment was sent, with the memo of the merchant's
    // acknowledgement when it has one.
    private void showPaymentSent(String merchantMemo) {
        RelativeLayout rLayout = findViewById(R.id.confirmationLayout);
        CheckView checkView = findViewById(R.id.check);
        LinearLayout checkLayout = findViewById(R.id.checkLayout);
        checkLayout.setOnClickListener(v -> leaveAfterPayment());
        if (merchantMemo != null && !merchantMemo.isEmpty()) {
            TextView memoView = findViewById(R.id.merchantMemo);
            memoView.setText(merchantMemo);
            memoView.setVisibility(View.VISIBLE);
        }
        rLayout.setVisibility(View.GONE);
        checkLayout.setVisibility(View.VISIBLE);
        checkView.check();
        afterPayment.setEnabled(true);
        if (payment.shownSent) {
            return;
        }
        payment.shownSent = true;
        Vibrations.buzz(getApplicationContext());
    }

    // signTransaction signs and sends the payment, and tells the screen's Payment how that went,
    // which tells the screen showing then.
    private void signTransaction(byte[] serializedTx, String paymentAddr, String memo, List<Long> inputVals, String password) {
        // Sign and send the payment once, also when the user confirms again or the fingerprint
        // is read again before the first attempt has finished (gcash/android-neutrino#23).
        Payment payment = this.payment;
        if (!payment.sending.compareAndSet(false, true)) {
            return;
        }
        Wallet wallet = Wallet.getInstance();
        ListenableFuture<Api.SignTransactionResponse> res = wallet.signTransactionAsync(serializedTx, inputVals, password);
        Futures.addCallback(res, new FutureCallback<Api.SignTransactionResponse>() {
            @Override
            public void onSuccess(Api.SignTransactionResponse result) {
                Settings.getInstance().setInvalidPinCount(0);
                if (result.getUnsignedInputIndexesList().size() > 0) {
                    payment.sending.set(false);
                    payment.failure.setValue(new Failure("Failed to sign transaction", true));
                    return;
                }

                // A BIP70 request may have no payment URL to send the payment to, and is then
                // paid as an address is.
                if (!isPaymentRequest || paymentURL == null || paymentURL.isEmpty()) {
                    publishTransaction(wallet, result, payment);
                } else {
                    postPaymentToMerchant(wallet, result, payment);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                Status status = Status.fromThrowable(t);
                String errStr = status.getDescription();
                Settings settings = Settings.getInstance();
                if (settings.getEncryptionType() == EncryptionType.PIN && Wallet.isWrongPassphrase(t)) {
                    errStr = "Invalid Pin";
                    settings.countInvalidPin();
                }
                // Nothing was sent, so the user may try again.
                payment.sending.set(false);
                payment.failure.setValue(new Failure(errStr, true));
            }
        }, ContextCompat.getMainExecutor(ConfirmationActivity.this));
    }

    private void publishTransaction(Wallet wallet, Api.SignTransactionResponse result, Payment payment) {
        ListenableFuture<Api.PublishTransactionResponse> resp = wallet.publishTransactionAsync(result.getTransaction().toByteArray(), memo);
        Futures.addCallback(resp, new FutureCallback<Api.PublishTransactionResponse>() {
            @Override
            public void onSuccess(Api.PublishTransactionResponse result) {
                payment.sent.setValue("");
            }

            @Override
            public void onFailure(Throwable t) {
                Status status = Status.fromThrowable(t);
                payment.failure.setValue(new Failure(status.getDescription(), false));
            }
        }, ContextCompat.getMainExecutor(ConfirmationActivity.this));
    }

    private void postPaymentToMerchant(Wallet wallet, Api.SignTransactionResponse result, Payment payment) {
        ListenableFuture<Api.PostPaymentResponse> resp = wallet.postPaymentAsync(refundAddress, refundAmount, result.getTransaction().toByteArray(), paymentURL, paymentProtocol, merchantData, memo);
        Futures.addCallback(resp, new FutureCallback<Api.PostPaymentResponse>() {
            @Override
            public void onSuccess(Api.PostPaymentResponse result) {
                payment.sent.setValue(result.getMemo());
            }

            @Override
            public void onFailure(Throwable t) {
                Status status = Status.fromThrowable(t);
                // The wallet records the payment only once the merchant has accepted it,
                // so it can be sent again.
                payment.sending.set(false);
                payment.failure.setValue(new Failure(status.getDescription(), true));
            }
        }, ContextCompat.getMainExecutor(ConfirmationActivity.this));
    }

}