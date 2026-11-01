package cash.bchd.android_neutrino;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.ProcessLifecycleOwner;

import cash.bchd.android_neutrino.wallet.Wallet;

/**
 * NeutrinoApp has WalletCloser watch the app go to the background and come back, to stop the
 * wallet once the app has been away for ten minutes and start it again when it returns.
 */
public class NeutrinoApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Handler main = new Handler(Looper.getMainLooper());
        WalletCloser closer = new WalletCloser(new WalletCloser.Wallets() {
            @Override
            public boolean exists() {
                return Wallet.getInstance() != null;
            }

            @Override
            public void start() {
                WalletStarter.restart(NeutrinoApp.this);
            }

            @Override
            public boolean stop() {
                Wallet wallet = Wallet.getInstance();
                if (wallet == null) {
                    return false;
                }
                wallet.stop();
                return true;
            }
        }, new WalletCloser.Delayer() {
            @Override
            public void postDelayed(Runnable task, long delayMillis) {
                main.postDelayed(task, delayMillis);
            }

            @Override
            public void cancel(Runnable task) {
                main.removeCallbacks(task);
            }
        }, WalletCloser.CLOSE_DELAY_MILLIS);
        ProcessLifecycleOwner.get().getLifecycle().addObserver(closer);
    }
}
