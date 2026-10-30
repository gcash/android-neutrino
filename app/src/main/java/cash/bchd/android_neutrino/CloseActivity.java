package cash.bchd.android_neutrino;

import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.TimeUnit;

import cash.bchd.android_neutrino.wallet.Wallet;

public class CloseActivity extends AppCompatActivity {
    protected static Wallet wallet;

    // The wallet runs on for 10 minutes after the app goes to the background, and then stops.
    // The stop runs on the main thread, as the screens' onStart does, so a screen that starts
    // again either cancels it or finds the wallet stopped and starts it again. A timer thread
    // could stop the wallet after onStart had found it running, as when Android froze the app
    // in the background and thawed it after the 10 minutes, which left no wallet running.
    private static final long CLOSE_DELAY_MILLIS = TimeUnit.MINUTES.toMillis(10);
    private static final Handler closeHandler = new Handler(Looper.getMainLooper());
    private static final Runnable closeWallet = () -> {
        if (wallet != null) {
            wallet.stop();
            wallet = null;
        }
    };

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
    protected void onStop() {
        super.onStop();
        closeHandler.removeCallbacks(closeWallet);
        closeHandler.postDelayed(closeWallet, CLOSE_DELAY_MILLIS);
    }

    public static void cancelCloseTimer() {
        closeHandler.removeCallbacks(closeWallet);
    }
}
