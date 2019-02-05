package cash.bchd.android_neutrino.wallet;

import android.content.Context;
import android.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Paths;

/**
 * Wallet represents an instance of a bchwallet. It will load and start the
 * bchwallet daemon and provide convience methods to the wallet's API calls.
 */
public class Wallet {

    private String configFilePath;

    /**
     * The wallet constructor takes in a context which it uses to derive the config file
     * path and appdatadir.
     */
    public Wallet(Context context, Config config) {
        this.configFilePath = config.getConfigFilePath();
        try {
            config.save(context);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Start will start the bchwallet daemon with the requested config options
     */
    public void Start() {
        mobile.Mobile.startWallet(this.configFilePath);
    }

    /**
     * Stop cleanly shuts down the wallet daemon. This must be called on close to guarantee
     * no data is corrupted.
     */
    public void Stop() {
        mobile.Mobile.stopWallet();
    }
}
