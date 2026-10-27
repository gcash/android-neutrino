package cash.bchd.android_neutrino;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Executor;

import cash.bchd.android_neutrino.wallet.Wallet;
import cash.bchd.android_neutrino.wallet.WalletSecret;
import walletrpc.Api;

/**
 * PassphraseUpgrade changes the wallet's private passphrase from what an earlier version used to a
 * random one sealed with WalletSecret, and records that the wallet uses it. The sealed passphrase
 * is saved before the wallet changes to it, so an upgrade that stops halfway, or the upgrade of a
 * restored wallet, reuses it.
 */
final class PassphraseUpgrade {

    interface Callback {
        void onUpgraded(String passphrase);

        void onFailed(Throwable t);
    }

    private PassphraseUpgrade() {
    }

    /**
     * run changes the wallet's passphrase from oldPassphrase and calls the callback on executor.
     */
    static void run(Wallet wallet, Settings settings, String oldPassphrase, Executor executor,
                    Callback callback) {
        String passphrase;
        try {
            String sealed = settings.getSealedPassphrase();
            if (sealed.isEmpty()) {
                passphrase = WalletSecret.newPassphrase();
                settings.setSealedPassphrase(WalletSecret.seal(passphrase));
            } else {
                passphrase = WalletSecret.open(sealed);
            }
        } catch (Exception e) {
            executor.execute(() -> callback.onFailed(e));
            return;
        }

        Futures.addCallback(changePassphrase(wallet, oldPassphrase, passphrase), new FutureCallback<Api.ChangePassphraseResponse>() {
            @Override
            public void onSuccess(Api.ChangePassphraseResponse result) {
                finish(settings);
                callback.onUpgraded(passphrase);
            }

            @Override
            public void onFailure(Throwable t) {
                // The wallet may use the sealed passphrase already, if the app stopped after
                // changing it and before recording that, which changing it to itself shows.
                Futures.addCallback(changePassphrase(wallet, passphrase, passphrase), new FutureCallback<Api.ChangePassphraseResponse>() {
                    @Override
                    public void onSuccess(Api.ChangePassphraseResponse result) {
                        finish(settings);
                        callback.onUpgraded(passphrase);
                    }

                    @Override
                    public void onFailure(Throwable ignored) {
                        callback.onFailed(t);
                    }
                }, executor);
            }
        }, executor);
    }

    private static ListenableFuture<Api.ChangePassphraseResponse> changePassphrase(Wallet wallet,
                                                                               String from, String to) {
        try {
            return wallet.changePasswordAsync(from, to);
        } catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    private static void finish(Settings settings) {
        settings.setEncryptionType(EncryptionType.KEYSTORE);
        settings.clearLegacyPassphrases();
        LegacyFingerprint.deleteKey();
    }
}
