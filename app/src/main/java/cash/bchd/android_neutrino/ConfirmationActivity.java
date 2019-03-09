package cash.bchd.android_neutrino;

import android.content.Intent;
import android.media.Image;
import android.os.Bundle;
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

import com.andrognito.pinlockview.IndicatorDots;
import com.andrognito.pinlockview.PinLockListener;
import com.andrognito.pinlockview.PinLockView;
import com.ebanx.swipebtn.OnStateChangeListener;
import com.ebanx.swipebtn.SwipeButton;

import java.util.ArrayList;
import java.util.List;

import cash.bchd.android_neutrino.wallet.Amount;
import cash.bchd.android_neutrino.wallet.Wallet;
import cdflynn.android.library.checkview.CheckView;

public class ConfirmationActivity extends AppCompatActivity {

    public static final int NO_AUTH = 0;
    public static final int PIN_AUTH = 1;
    public static final int FINGERPRINT_AUTH = 2;
    PinLockView mPinLockView;
    boolean showingPinPad = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_confirmation);

        CloseActivity.cancelCloseTimer();

        Intent intent = getIntent();
        EncryptionType encType = Settings.getInstance().getEncryptionType();
        SwipeButton swipeButton = (SwipeButton) findViewById(R.id.swipe_btn);
        if (encType == EncryptionType.PIN) {
            RelativeLayout pinLayout = (RelativeLayout) findViewById(R.id.pinLayout);
            pinLayout.setVisibility(View.VISIBLE);
        } else if (encType == EncryptionType.FINGERPRINT) {
            ImageView fingerprint = (ImageView) findViewById(R.id.fingerprint);
            fingerprint.setVisibility(View.VISIBLE);
        } else {
            swipeButton.setVisibility(View.VISIBLE);
        }

        String paymentAddr = intent.getStringExtra("paymentAddress");
        String bchAmount = intent.getStringExtra("amountBCH");
        String formattedFiat = intent.getStringExtra("amountFiat");
        long txFee = intent.getLongExtra("fee", 0);
        byte[] serializedTx = intent.getByteArrayExtra("serializedTransaction");
        ArrayList<String> inputStrings = intent.getStringArrayListExtra("inputVals");

        List<Long> inputVals = new ArrayList<Long>();
        for (String s : inputStrings) {
            inputVals.add(Long.valueOf(s));
        }

        String memo = intent.getStringExtra("memo");
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

        mPinLockView = (PinLockView) findViewById(R.id.pin_lock_view);
        mPinLockView.setPinLockListener(new PinLockListener() {
            @Override
            public void onComplete(String pin) {
                System.out.println("On pin complete");
            }

            @Override
            public void onEmpty() {
            }

            @Override
            public void onPinChange(int pinLength, String intermediatePin) {
            }
        });

        IndicatorDots mIndicatorDots = (IndicatorDots) findViewById(R.id.indicator_dots);
        mPinLockView.attachIndicatorDots(mIndicatorDots);


        RelativeLayout keyLayout = (RelativeLayout) findViewById(R.id.keyLayout);
        CardView detailsCardView = (CardView) findViewById(R.id.detailsCard);
        mIndicatorDots.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!showingPinPad) {
                    detailsCardView.setVisibility(View.GONE);
                    keyLayout.setVisibility(View.VISIBLE);
                } else {
                    mPinLockView.setVisibility(View.GONE);
                    keyLayout.setVisibility(View.VISIBLE);
                }
            }
        });

        swipeButton.setOnStateChangeListener(new OnStateChangeListener() {
            @Override
            public void onStateChange(boolean active) {
                Wallet wallet = Wallet.getInstance();
                byte[] signedTx = null;
                try {
                    signedTx = wallet.signTransaction(serializedTx, inputVals, Wallet.DEFAULT_PASSPHRASE);
                } catch (Exception e) {
                    CoordinatorLayout layout = (CoordinatorLayout) findViewById(R.id.confirmationMainLayout);
                    Snackbar snackbar = Snackbar.make(layout, "Error signing transaction.", Snackbar.LENGTH_LONG);
                    snackbar.show();
                    e.printStackTrace();
                    return;
                }
                try {
                    wallet.publishTransaction(signedTx, paymentAddr, memo);
                } catch (Exception e) {
                    CoordinatorLayout layout = (CoordinatorLayout) findViewById(R.id.confirmationMainLayout);
                    Snackbar snackbar = Snackbar.make(layout, "Error broadcasting transaction.", Snackbar.LENGTH_LONG);
                    snackbar.show();
                    e.printStackTrace();
                    return;
                }

                RelativeLayout rLayout = (RelativeLayout) findViewById(R.id.confirmationLayout);
                CheckView checkView = (CheckView) findViewById(R.id.check);
                RelativeLayout checkLayout = (RelativeLayout) findViewById(R.id.checkLayout);

                rLayout.setVisibility(View.GONE);
                checkLayout.setVisibility(View.VISIBLE);
                checkView.check();
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
        RelativeLayout checkLayout = (RelativeLayout) findViewById(R.id.checkLayout);
        if (checkLayout.getVisibility() == View.VISIBLE) {
            Intent setIntent = new Intent(this, MainActivity.class);
            startActivity(setIntent);
        } else {
            super.onBackPressed();
        }
    }
}