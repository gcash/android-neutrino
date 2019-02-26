package cash.bchd.android_neutrino;

import android.support.v7.app.AppCompatActivity;

import java.util.Timer;
import java.util.TimerTask;

import cash.bchd.android_neutrino.wallet.Wallet;

public class CloseActivity extends AppCompatActivity {
    protected static Wallet wallet;
    Timer closeTimer;
    @Override
    protected void onDestroy() {
        super.onDestroy();
        wallet.stop();
        wallet = null;
    }

    @Override
    // Allow running as a background process for 10 minutes after this activity is stopped
    // before stopping the daemon.
    protected void onStop() {
        super.onStop();
        closeTimer = new Timer();
        closeTimer.schedule(
            new TimerTask() {
                @Override
                public void run() {
                    wallet.stop();
                    wallet = null;
                }
                        },
            600000);
    }

    public void cancelCloseTimer() {
        if (this.closeTimer != null) {
            this.closeTimer.cancel();
            this.closeTimer = null;
        }
    }
}
