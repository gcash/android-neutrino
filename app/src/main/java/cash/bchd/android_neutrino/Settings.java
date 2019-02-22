package cash.bchd.android_neutrino;

import android.content.SharedPreferences;

public class Settings {

    private static final String INITIALIZED_KEY = "Initialized";
    private static final String MNEMONIC_KEY = "Mnemonic";
    private static final String BLOCKS_ONLY_KEY = "BlocksOnly";

    SharedPreferences prefs;

    Settings(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    public void setWalletInitialized(boolean initialized) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putBoolean(INITIALIZED_KEY, initialized);
        editor.apply();
    }

    public Boolean getWalletInitialized() {
        boolean initialized = prefs.getBoolean(INITIALIZED_KEY, false);
        return initialized;
    }

    public void setMnemonic(String mnemonic) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putString(MNEMONIC_KEY, mnemonic);
        editor.apply();
    }

    public String getMnemonic() {
        String mnemonic = prefs.getString(MNEMONIC_KEY, "");
        return mnemonic;
    }

    public void setBlocksOnly(boolean blocksOnly) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putBoolean(BLOCKS_ONLY_KEY, blocksOnly);
        editor.apply();
    }

    public Boolean getBlocksOnly() {
        boolean blocksOnly = prefs.getBoolean(BLOCKS_ONLY_KEY, false);
        return blocksOnly;
    }
}
