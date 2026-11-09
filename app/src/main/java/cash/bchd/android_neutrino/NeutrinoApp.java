package cash.bchd.android_neutrino;

import android.app.Application;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;

import cash.bchd.android_neutrino.wallet.TokenLabelFile;
import cash.bchd.android_neutrino.wallet.Wallet;
import walletrpc.Api;

/**
 * NeutrinoApp has WalletCloser watch the app go to the background and come back, to stop the
 * wallet once the app has been away for ten minutes and start it again when it returns, and tells
 * the wallet when the device moves to another network.
 */
public class NeutrinoApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Before any screen, as Android can bring back any of them after it ended the app.
        TokenLabelFile.open(this, Settings.get(this).getNetwork());
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
        watchNetwork();
    }

    // watchNetwork tells the wallet when the device's default network changes, as from cellular to
    // Wi-Fi, which can leave its peers' connections gone without either side being told, so that
    // it connects again at once rather than when it notices, which can take minutes.
    private void watchNetwork() {
        ConnectivityManager connectivity = getSystemService(ConnectivityManager.class);
        if (connectivity == null) {
            return;
        }
        NetworkChanges changes = new NetworkChanges();
        connectivity.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                Wallet wallet = Wallet.getInstance();
                if (!changes.changed(network.toString()) || wallet == null || !wallet.isRunning()) {
                    return;
                }
                // A wallet from before the call answers it's unknown, which costs nothing.
                Futures.addCallback(wallet.networkChangedAsync(), new FutureCallback<Api.NetworkChangedResponse>() {
                    @Override
                    public void onSuccess(Api.NetworkChangedResponse reply) {
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                    }
                }, MoreExecutors.directExecutor());
            }
        });
    }
}
