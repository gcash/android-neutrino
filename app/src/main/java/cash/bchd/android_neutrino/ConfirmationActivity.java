package cash.bchd.android_neutrino;

import android.content.Intent;
import android.media.Image;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.andrognito.pinlockview.IndicatorDots;
import com.andrognito.pinlockview.PinLockListener;
import com.andrognito.pinlockview.PinLockView;
import com.ebanx.swipebtn.OnStateChangeListener;
import com.ebanx.swipebtn.SwipeButton;

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
        int authType = NO_AUTH;
        SwipeButton swipeButton = (SwipeButton) findViewById(R.id.swipe_btn);
        if (intent.getExtras() != null) {
            authType = intent.getExtras().getInt("authType");
        }
        if (authType == PIN_AUTH) {
            RelativeLayout pinLayout = (RelativeLayout) findViewById(R.id.pinLayout);
            pinLayout.setVisibility(View.VISIBLE);
        } else if (authType == FINGERPRINT_AUTH) {
            ImageView fingerprint = (ImageView) findViewById(R.id.fingerprint);
            fingerprint.setVisibility(View.VISIBLE);
        } else {
            swipeButton.setVisibility(View.VISIBLE);
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
                System.out.println("Here");
            }
        });
    }
}