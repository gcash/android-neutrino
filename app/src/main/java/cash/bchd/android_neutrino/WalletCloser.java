package cash.bchd.android_neutrino;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import java.util.concurrent.TimeUnit;

/**
 * WalletCloser stops the wallet once the app has been in the background for ten minutes, and
 * starts it again when the app comes back, whichever of its screens comes back. It watches the
 * whole app, which moving between its screens or rotating one doesn't leave. The main screen's
 * onStop, which started the countdown before, also comes when another of the app's screens
 * covers it, and the other screens cancelled the countdown before it started, so ten minutes on
 * the Send or Receive screen stopped the wallet under them. The countdown runs on the main
 * thread, as the app's lifecycle callbacks do, so coming back either cancels it or finds the
 * wallet stopped, also when Android froze the app in the background and thawed it after the ten
 * minutes. It starts only a wallet that it stopped: a wallet that has yet to be created is the
 * main screen's to create, as only it keeps the new wallet's recovery phrase.
 */
final class WalletCloser implements DefaultLifecycleObserver {

    static final long CLOSE_DELAY_MILLIS = TimeUnit.MINUTES.toMillis(10);

    /**
     * Wallets starts and stops the app's wallet.
     */
    interface Wallets {
        /**
         * exists returns whether a wallet was started and hasn't been stopped since.
         */
        boolean exists();

        void start();

        /**
         * stop stops the wallet, and returns whether there was one to stop.
         */
        boolean stop();
    }

    /**
     * Delayer runs a task after a delay, on the main thread, unless it's cancelled first.
     */
    interface Delayer {
        void postDelayed(Runnable task, long delayMillis);

        void cancel(Runnable task);
    }

    private final Wallets wallets;
    private final Delayer delayer;
    private final long delayMillis;
    private final Runnable close = this::close;
    private boolean closed;

    WalletCloser(Wallets wallets, Delayer delayer, long delayMillis) {
        this.wallets = wallets;
        this.delayer = delayer;
        this.delayMillis = delayMillis;
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        delayer.cancel(close);
        if (closed) {
            closed = false;
            if (!wallets.exists()) {
                wallets.start();
            }
        }
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        delayer.cancel(close);
        delayer.postDelayed(close, delayMillis);
    }

    private void close() {
        if (wallets.stop()) {
            closed = true;
        }
    }
}
