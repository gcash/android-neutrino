package cash.bchd.android_neutrino;

import android.content.SharedPreferences;

public class Settings {

    private static final String INITIALIZED_KEY = "Initialized";
    private static final String MNEMONIC_KEY = "Mnemonic";
    private static final String BLOCKS_ONLY_KEY = "BlocksOnly";
    private static final String LAST_BALANCE_KEY = "LastBalance";
    private static final String FIAT_CURRENCY_KEY = "FiatCurrency";
    private static final String LAST_ADDRESS_KEY = "LastAddress";

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

    public void setLastBalance(long balance) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putLong(LAST_BALANCE_KEY, balance);
        editor.apply();
    }

    public long getLastBalance() {
        long balance = prefs.getLong(LAST_BALANCE_KEY, 0);
        return balance;
    }

    public void setFiatCurrency(String currency) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putString(FIAT_CURRENCY_KEY, currency);
        editor.apply();
    }

    public String getFiatCurrency() {
        String currency = prefs.getString(FIAT_CURRENCY_KEY, "usd");
        return currency;
    }

    public void setLastAddress(String addr) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putString(LAST_ADDRESS_KEY, addr);
        editor.apply();
    }

    public String getLastAddress() {
        String addr = prefs.getString(LAST_ADDRESS_KEY, "");
        return addr;
    }
}
