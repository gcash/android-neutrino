package cash.bchd.android_neutrino;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import cash.bchd.android_neutrino.wallet.Network;
import cash.bchd.android_neutrino.wallet.Wallet;

/**
 * Networks shows which network the wallet runs on, and switches it to another.
 */
final class Networks {

    private Networks() {
    }

    /**
     * name returns the name of the network to show.
     */
    static String name(Context context, Network network) {
        switch (network) {
            case TESTNET4:
                return context.getString(R.string.network_testnet4);
            default:
                return context.getString(R.string.network_mainnet);
        }
    }

    /**
     * showBadge shows the activity's network badge, with the network's name, when the wallet runs
     * on a test network, so that test coins aren't taken for real ones.
     */
    static void showBadge(Activity activity, Network network) {
        TextView badge = activity.findViewById(R.id.networkBadge);
        if (network == Network.MAINNET) {
            badge.setVisibility(View.GONE);
            return;
        }
        badge.setText(name(activity, network));
        badge.setVisibility(View.VISIBLE);
    }

    /**
     * switchTo makes the app run the network's wallet: it stops the wallet that runs, and starts
     * the app again on the network, as restoring a wallet does. A network's wallet is created the
     * first time the app runs on it.
     */
    static void switchTo(Activity activity, Network network) {
        Settings.get(activity).setNetwork(network);
        Wallet wallet = Wallet.getInstance();
        if (wallet != null) {
            wallet.stop();
        }
        Intent intent = new Intent(activity, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }
}
