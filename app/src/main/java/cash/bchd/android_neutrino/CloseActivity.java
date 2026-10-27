package cash.bchd.android_neutrino;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Timer;
import java.util.TimerTask;

import cash.bchd.android_neutrino.wallet.Wallet;

public class CloseActivity extends AppCompatActivity {
    protected static Wallet wallet;
    protected static Timer closeTimer;
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Keep the wallet running when the activity is only recreated, such as
        // after the screen rotated.
        if (isFinishing() && wallet != null) {
            wallet.stop();
            wallet = null;
        }
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
                    if (wallet != null) {
                        wallet.stop();
                        wallet = null;
                    }
                }
                        },
            600000);
    }

    public static void cancelCloseTimer() {
        if (closeTimer != null) {
            closeTimer.cancel();
            closeTimer = null;
        }
    }
}
