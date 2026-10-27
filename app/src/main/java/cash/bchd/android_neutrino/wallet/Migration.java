package cash.bchd.android_neutrino.wallet;

import android.content.Context;

import java.io.File;

public class Migration {

    private int currentVersion;

    public Migration(int currentVersion) {
        this.currentVersion = currentVersion;
    }

    public int MigrateUp(Context context) {
        if (this.currentVersion <= 1){
            deleteNeutrinoFiles(context);
            this.currentVersion++;
        }
        if (this.currentVersion == 2) {
            // Neutrino stopped syncing at the November 2020 network upgrade, at
            // height 661647. Its headers, filters and peers are only a cache, so
            // delete them for the current neutrino to sync them again. The wallet
            // keeps its keys and transactions and catches up from where it was.
            deleteNeutrinoFiles(context);
            this.currentVersion++;
        }
        return this.currentVersion;
    }

    private static void deleteNeutrinoFiles(Context context) {
        File blockHeaders = new File(context.getDataDir().getPath() + "/mainnet/block_headers.bin");
        blockHeaders.delete();

        File peers = new File(context.getDataDir().getPath() + "/mainnet/peers.json");
        peers.delete();

        File filterHeaders = new File(context.getDataDir().getPath() + "/mainnet/reg_filter_headers.bin");
        filterHeaders.delete();

        File nuetrinoDB = new File(context.getDataDir().getPath() + "/mainnet/neutrino.db");
        nuetrinoDB.delete();
    }

}
