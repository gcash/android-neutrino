package cash.bchd.android_neutrino;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import cash.bchd.android_neutrino.wallet.Config;
import cash.bchd.android_neutrino.wallet.Migration;
import cash.bchd.android_neutrino.wallet.Wallet;
import cash.bchd.android_neutrino.wallet.WalletEventListener;

/**
 * WalletStarter starts the app's wallet from its settings: for the main screen, which starts it
 * when the app opens and attaches to it, and for WalletCloser, which starts it again when the
 * app comes back after it stopped the wallet.
 */
final class WalletStarter {

    private WalletStarter() {
    }

    /**
     * start starts a wallet from the settings and returns it. The caller makes sure that none
     * runs, and that the wallet may reach the nodes it's set to.
     */
    static Wallet start(Context context, Settings settings) {
        String[] peers = settings.getPeerList();
        String[] none = new String[0];
        boolean peersOnly = settings.getPeersOnly() && peers.length > 0;
        String bchdIP = settings.getBchdIP();
        long birthday = settings.getWalletBirthday();
        if (birthday == 0) {
            birthday = System.currentTimeMillis() / 1000;
            settings.setWalletBirthday(birthday);
        }

        Migration migration = new Migration(settings.getRepoVersion());
        int newRepoVersion = migration.MigrateUp(context);
        settings.setRepoVersion(newRepoVersion);

        Config cfg = new Config(context.getDataDir().getPath(), settings.getNetwork(), !settings.getWalletInitialized(),
                bchdIP.equals(""), settings.getBlocksOnly(), peersOnly ? peers : none, peersOnly ? none : peers,
                settings.getBchdIP(), settings.getBchdUsername(),
                settings.getBchdPassword(), settings.getBchdCert(), birthday);
        Wallet wallet = new Wallet(context, cfg);
        wallet.start();
        return wallet;
    }

    /**
     * restart starts the wallet again and loads it, after WalletCloser stopped it, unless the
     * user has yet to answer whether the app may reach the wallet's nodes on the local network,
     * which the main screen asks when it shows.
     */
    static void restart(Context context) {
        Settings settings = Settings.get(context);
        List<String> local = localNodesWithoutPermission(context, settings);
        if (!local.isEmpty() && !TextUtils.join(",", local).equals(settings.getLocalNetworkAsked())) {
            return;
        }
        Wallet wallet = start(context, settings);
        wallet.loadWallet(new WalletEventListener() {
            @Override
            public void onWalletCreated(String seed) {
                // The wallet existed when it stopped, but should it have been created, its
                // recovery phrase is kept, as the main screen keeps it.
                settings.setWalletInitialized(true);
                settings.setMnemonic(seed);
            }
        }, settings.getMnemonic());
    }

    /**
     * localNodesWithoutPermission returns the nodes the wallet is set to that are on the local
     * network, when the app may not reach them: Android 17 and newer let it only with the user's
     * permission.
     */
    static List<String> localNodesWithoutPermission(Context context, Settings settings) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK)
                == PackageManager.PERMISSION_GRANTED) {
            return Collections.emptyList();
        }
        List<String> nodes = new ArrayList<>(Arrays.asList(settings.getPeerList()));
        String bchdIP = settings.getBchdIP();
        if (!bchdIP.isEmpty()) {
            nodes.add(bchdIP);
        }
        return LocalNetwork.local(nodes);
    }
}
