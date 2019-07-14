package cash.bchd.android_neutrino.wallet;

import android.content.Context;

import java.io.File;

public class Migration {

    private int currentVersion;

    public Migration(int currentVersion) {
        this.currentVersion = currentVersion;
    }

    public int MigrateUp(Context context) {
        if (this.currentVersion == 0){
            File blockHeaders = new File(context.getDataDir().getPath() + "/mainnet/block_headers.bin");
            blockHeaders.delete();

            File filterHeaders = new File(context.getDataDir().getPath() + "/mainnet/reg_filter_headers.bin");
            filterHeaders.delete();

            File nuetrinoDB = new File(context.getDataDir().getPath() + "/mainnet/neutrino.db");
            nuetrinoDB.delete();
            this.currentVersion++;
        }
        return this.currentVersion;
    }

}
