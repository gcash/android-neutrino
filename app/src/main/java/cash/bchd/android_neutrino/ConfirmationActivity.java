package cash.bchd.android_neutrino;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.Image;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.ebanx.swipebtn.OnStateChangeListener;
import com.ebanx.swipebtn.SwipeButton;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.List;

import cash.bchd.android_neutrino.wallet.Amount;
import cash.bchd.android_neutrino.wallet.Wallet;
import cdflynn.android.library.checkview.CheckView;
import io.grpc.Status;
import walletrpc.Api;

public class ConfirmationActivity extends AppCompatActivity {

    byte[] serializedTx;
    List<Long> inputVals;
    String paymentAddr;
    String memo;
    SwipeButton swipeButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_confirmation);

        CloseActivity.cancelCloseTimer();

        Intent intent = getIntent();
        EncryptionType encType = Settings.getInstance().getEncryptionType();
        swipeButton = (SwipeButton) findViewById(R.id.swipe_btn);
        if (encType == EncryptionType.PIN) {
            swipeButton.setVisibility(View.VISIBLE);
        } else if (encType == EncryptionType.FINGERPRINT) {
            ImageView fingerprint = (ImageView) findViewById(R.id.fingerprint);
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

        inputVals = new ArrayList<Long>();
        for (String s : inputStrings) {
            inputVals.add(Long.valueOf(s));
        }

        memo = intent.getStringExtra("memo");
        String label = intent.getStringExtra("label");

        TextView amountTxtView = (TextView) findViewById(R.id.confirmBchAmount);
        amountTxtView.setText(bchAmount + " BCH");

        TextView fiatAmountTxtView = (TextView) findViewById(R.id.confirmFiatAmount);
        fiatAmountTxtView.setText(formattedFiat);

        TextView payTo = (TextView) findViewById(R.id.payTo);
        if (label != null && !label.equals("")) {
            payTo.setText(label);
        } else {
            TextView payToLabel = (TextView) findViewById(R.id.payToLabel);
            View div7 = (View) findViewById(R.id.divider7);
            payTo.setVisibility(View.GONE);
            payToLabel.setVisibility(View.GONE);
            div7.setVisibility(View.GONE);
            TextView paymentAddrLabel = (TextView) findViewById(R.id.paymentAddrLabel);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) paymentAddrLabel.getLayoutParams();
            params.setMargins(params.leftMargin, 30, params.rightMargin, params.bottomMargin);
            paymentAddrLabel.setLayoutParams(params);
        }

        TextView paymentAddress = (TextView) findViewById(R.id.paymentAddress);
        paymentAddress.setText(paymentAddr);

        TextView networkFee = (TextView) findViewById(R.id.networkFee);
        networkFee.setText(new Amount(txFee).toString() + " BCH");

        TextView memoTxtView = (TextView) findViewById(R.id.memoConfirmation);
        if (memo != null && !memo.equals("")) {
            memoTxtView.setText(memo);
        } else {
            TextView memoLabel = (TextView) findViewById(R.id.memoLabel);
            memoTxtView.setVisibility(View.GONE);
            memoLabel.setVisibility(View.GONE);
        }


        CardView detailsCardView = (CardView) findViewById(R.id.detailsCard);

        swipeButton.setOnStateChangeListener(new OnStateChangeListener() {
            @Override
            public void onStateChange(boolean active) {
                if (active) {
                    if (encType == EncryptionType.PIN) {
                        Settings settings = Settings.getInstance();
                        if (settings.getInvalidPinCount() >= 3 && settings.getLastInvalidPin() + 300000 > System.currentTimeMillis()) {
                            CoordinatorLayout layout = (CoordinatorLayout) findViewById(R.id.confirmationMainLayout);
                            Snackbar snackbar = Snackbar.make(layout, "Too many invalid pin attempts. Wait five minutes.", Snackbar.LENGTH_LONG);
                            snackbar.show();
                            return;
                        }

                        Intent intent = new Intent(ConfirmationActivity.this, PinActivity.class);
                        intent.putExtra("enterOnly", true);
                        startActivityForResult(intent, 1234);
                        return;
                    }
                    signTransaction(serializedTx, paymentAddr, memo, inputVals, Wallet.DEFAULT_PASSPHRASE);
                }
            }
        });
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
        LinearLayout checkLayout = (LinearLayout) findViewById(R.id.checkLayout);
        if (checkLayout.getVisibility() == View.VISIBLE) {
            Intent setIntent = new Intent(this, MainActivity.class);
            startActivity(setIntent);
        } else {
            super.onBackPressed();
        }
    }

    private void signTransaction(byte[] serializedTx, String paymentAddr, String memo, List<Long> inputVals, String password) {
        Wallet wallet = Wallet.getInstance();
        byte[] signedTx = null;
        CoordinatorLayout layout = (CoordinatorLayout) findViewById(R.id.confirmationMainLayout);
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

                ListenableFuture<Api.PublishTransactionResponse> resp = wallet.publishTransactionAsync(result.getTransaction().toByteArray(), paymentAddr, memo);
                Futures.addCallback(resp, new FutureCallback<Api.PublishTransactionResponse>() {
                    @Override
                    public void onSuccess(Api.PublishTransactionResponse result) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                RelativeLayout rLayout = (RelativeLayout) findViewById(R.id.confirmationLayout);
                                CheckView checkView = (CheckView) findViewById(R.id.check);
                                LinearLayout checkLayout = (LinearLayout) findViewById(R.id.checkLayout);

                                rLayout.setVisibility(View.GONE);
                                checkLayout.setVisibility(View.VISIBLE);
                                checkView.check();
                                Vibrator vibrator = (Vibrator) getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
                                vibrator.vibrate(500);
                            }
                        });
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
            }

            @Override
            public void onFailure(Throwable t) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Status status = Status.fromThrowable(t);
                        String errStr = status.getDescription();
                        if (status.getDescription().equals("invalid passphrase for master private key")) {
                            errStr = "Invalid Pin";
                            Settings settings = Settings.getInstance();
                            int invalidCount = settings.getInvalidPinCount();
                            invalidCount++;
                            settings.setInvalidPinCount(invalidCount);
                            settings.setLastInvalidPin(System.currentTimeMillis());
                        }
                        Snackbar snackbar = Snackbar.make(layout, errStr, Snackbar.LENGTH_LONG);
                        snackbar.show();
                        swipeButton.setHasActivationState(false);
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