package cash.bchd.android_neutrino;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import cash.bchd.android_neutrino.wallet.Network;
import cash.bchd.android_neutrino.wallet.WalletSecret;

/**
 * Settings keeps the app's settings. Each network's wallet has its own, such as its recovery
 * phrase, passphrase and peers: mainnet's in the default preferences, where earlier versions kept
 * them, and another network's in a file of its own. The others, such as the fiat currency and the
 * fee, are the same on every network and are kept in the default preferences.
 */
public class Settings {

    // The settings of the app, the same on every network.
    private static final String NETWORK_KEY = "Network";
    private static final String BLOCKS_ONLY_KEY = "BlocksOnly";
    private static final String FIAT_CURRENCY_KEY = "FiatCurrency";
    private static final String FEE_PER_BYTE_KEY = "FeePerByte";
    private static final String DEFAULT_LABEL_KEY = "DefaultLabel";
    private static final String DEFAULT_MEMO_KEY = "DefaultMemo";
    private static final String LAST_NOTIFICATION_KEY = "LastNotification";
    private static final String REPO_VERSION_KEY = "RepoVersion";
    private static final String NOTIFICATIONS_REQUESTED_KEY = "NotificationsRequested";

    // The settings of a network's wallet.
    private static final String INITIALIZED_KEY = "Initialized";
    private static final String MNEMONIC_KEY = "Mnemonic";
    private static final String SEALED_MNEMONIC_KEY = "SealedMnemonic";
    private static final String LAST_BALANCE_KEY = "LastBalance";
    private static final String LAST_ADDRESS_KEY = "LastAddress";
    private static final String LAST_BLOCK_HEIGHT_KEY = "LastBlockHeight";
    private static final String LAST_BLOCK_HASH_KEY = "LastBlockHash";
    private static final String BCHD_IP_KEY = "BchdIP";
    private static final String BCHD_USERNAME_KEY = "BchdUsername";
    private static final String BCHD_PASSWORD_KEY = "BchdPassword";
    private static final String BCHD_CERT_KEY = "BchdCert";
    private static final String ENCRYPTION_TYPE_KEY = "EncryptionType";
    private static final String INVALID_PIN_COUNT_KEY = "InvalidPinCount";
    private static final String LAST_INVALID_PIN_KEY = "LastInvalidPin";
    private static final String ENCRYPTED_PASSWORD_KEY = "EncryptedPassword";
    private static final String FINGERPRINT_IV_KEY = "FingerprintIV";
    private static final String SEALED_PASSPHRASE_KEY = "SealedPassphrase";
    private static final String BACKUP_REMINDER_KEY = "BackupReminder";
    private static final String WALLET_BIRTHDAY_KEY = "WalletBirthday";
    private static final String SCAN_HEIGHT_KEY = "ScanHeight";
    private static final String PEERS_KEY = "Peers";
    private static final String PEERS_ONLY_KEY = "PeersOnly";
    private static final String LOCAL_NETWORK_ASKED_KEY = "LocalNetworkAsked";

    // The settings of the app, and those of the network's wallet, which are the same on mainnet.
    private final SharedPreferences appPrefs;
    private final SharedPreferences prefs;
    private final Network network;

    private static Settings instance;

    Settings(SharedPreferences appPrefs, SharedPreferences prefs, Network network) {
        this.appPrefs = appPrefs;
        this.prefs = prefs;
        this.network = network;
        instance = this;
    }

    /**
     * load reads the settings of the network the app was switched to last, or mainnet, and makes
     * them the instance.
     */
    static Settings load(Context context) {
        SharedPreferences appPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        Network network = Network.fromId(appPrefs.getString(NETWORK_KEY, Network.MAINNET.id));
        SharedPreferences prefs = network == Network.MAINNET ? appPrefs
                : context.getSharedPreferences(walletPrefsName(network), Context.MODE_PRIVATE);
        return new Settings(appPrefs, prefs, network);
    }

    /**
     * get returns the instance, after loading it when the app's process was started for another
     * screen than MainActivity, as when Android restores a screen of an app it had stopped.
     */
    static Settings get(Context context) {
        Settings settings = instance;
        return settings != null ? settings : load(context);
    }

    // The name of the preferences file of a network's wallet other than mainnet's.
    static String walletPrefsName(Network network) {
        return "wallet_" + network.id;
    }

    public static Settings getInstance() {
        return instance;
    }

    /**
     * getNetwork returns the network whose wallet these settings are for.
     */
    public Network getNetwork() {
        return network;
    }

    /**
     * setNetwork makes the app run the network's wallet once it loads the settings again.
     */
    void setNetwork(Network network) {
        appPrefs.edit().putString(NETWORK_KEY, network.id).apply();
    }

    void setWalletInitialized(boolean initialized) {
        putBoolean(INITIALIZED_KEY, initialized);
    }

    Boolean getWalletInitialized() {
        return prefs.getBoolean(INITIALIZED_KEY, false);
    }

    // The recovery phrase, kept until the user backed it up, sealed with WalletSecret. Without the
    // Keystore it stays plain text, as earlier versions kept it, rather than be lost; an empty
    // phrase forgets it.
    public void setMnemonic(String mnemonic) {
        if (mnemonic.isEmpty()) {
            prefs.edit().remove(MNEMONIC_KEY).remove(SEALED_MNEMONIC_KEY).apply();
            return;
        }
        try {
            String sealed = WalletSecret.seal(mnemonic);
            prefs.edit().putString(SEALED_MNEMONIC_KEY, sealed).remove(MNEMONIC_KEY).apply();
        } catch (Exception e) {
            e.printStackTrace();
            prefs.edit().putString(MNEMONIC_KEY, mnemonic).remove(SEALED_MNEMONIC_KEY).apply();
        }
    }

    // getMnemonic returns the recovery phrase, or "" once it was backed up, and seals the plain
    // text phrase of an earlier version.
    public String getMnemonic() {
        String plain = prefs.getString(MNEMONIC_KEY, "");
        if (!plain.isEmpty()) {
            setMnemonic(plain);
            return plain;
        }
        String sealed = prefs.getString(SEALED_MNEMONIC_KEY, "");
        if (sealed.isEmpty()) {
            return "";
        }
        try {
            return WalletSecret.open(sealed);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    void setBlocksOnly(boolean blocksOnly) {
        appPrefs.edit().putBoolean(BLOCKS_ONLY_KEY, blocksOnly).apply();
    }

    Boolean getBlocksOnly() {
        return appPrefs.getBoolean(BLOCKS_ONLY_KEY, false);
    }

    void setLastBalance(long balance) {
        putLong(LAST_BALANCE_KEY, balance);
    }

    long getLastBalance() {
        return prefs.getLong(LAST_BALANCE_KEY, 0);
    }

    void setFiatCurrency(String currency) {
        appPrefs.edit().putString(FIAT_CURRENCY_KEY, currency).apply();
    }

    String getFiatCurrency() {
        return appPrefs.getString(FIAT_CURRENCY_KEY, "usd");
    }

    void setLastAddress(String addr) {
        putString(LAST_ADDRESS_KEY, addr);
    }

    String getLastAddress() {
        return prefs.getString(LAST_ADDRESS_KEY, "");
    }

    void setLastBlockHeight(int blockHeight) {
        putInt(LAST_BLOCK_HEIGHT_KEY, blockHeight);
    }

    int getLastBlockHeight() {
        return prefs.getInt(LAST_BLOCK_HEIGHT_KEY, 0);
    }

    void setLastBlockHash(String blockHash) {
        putString(LAST_BLOCK_HASH_KEY, blockHash);
    }

    String getLastBlockHash() {
        return prefs.getString(LAST_BLOCK_HASH_KEY, "");
    }

    void setFeePerByte(int satPerByte) {
        appPrefs.edit().putInt(FEE_PER_BYTE_KEY, satPerByte).apply();
    }

    int getFeePerByte() {
        // 1 sat/byte is the minimum relay fee of Bitcoin Cash nodes, which
        // confirms transactions in the next block unless blocks are full.
        return appPrefs.getInt(FEE_PER_BYTE_KEY, 1);
    }

    void setDefaultLabel(String label) {
        appPrefs.edit().putString(DEFAULT_LABEL_KEY, label).apply();
    }

    String getDefaultLabel() {
        return appPrefs.getString(DEFAULT_LABEL_KEY, "");
    }

    void setDefaultMemo(String memo) {
        appPrefs.edit().putString(DEFAULT_MEMO_KEY, memo).apply();
    }

    String getDefaultMemo() {
        return appPrefs.getString(DEFAULT_MEMO_KEY, "");
    }

    void setBchdIP(String ip) {
        putString(BCHD_IP_KEY, ip);
    }

    String getBchdIP() {
        return prefs.getString(BCHD_IP_KEY, "");
    }

    void setBchdUsername(String username) {
        putString(BCHD_USERNAME_KEY, username);
    }

    String getBchdUsername() {
        return prefs.getString(BCHD_USERNAME_KEY, "");
    }

    void setBchdPassword(String password) {
        putString(BCHD_PASSWORD_KEY, password);
    }

    String getBchdPassword() {
        return prefs.getString(BCHD_PASSWORD_KEY, "");
    }

    void setBchdCert(String cert) {
        putString(BCHD_CERT_KEY, cert);
    }

    String getBchdCert() {
        return prefs.getString(BCHD_CERT_KEY, "");
    }

    void setEncryptionType(EncryptionType et) {
        putString(ENCRYPTION_TYPE_KEY, et.toString());
    }

    EncryptionType getEncryptionType() {
        String t = prefs.getString(ENCRYPTION_TYPE_KEY, "unencrypted");
        return EncryptionType.fromString(t);
    }

    void setInvalidPinCount(int count) {
        putInt(INVALID_PIN_COUNT_KEY, count);
    }

    int getInvalidPinCount() {
        return prefs.getInt(INVALID_PIN_COUNT_KEY, 0);
    }

    void setLastInvalidPin(long timestamp) {
        putLong(LAST_INVALID_PIN_KEY, timestamp);
    }

    long getLastInvalidPin() {
        return prefs.getLong(LAST_INVALID_PIN_KEY, 0);
    }

    // The PIN of earlier versions is refused for five minutes after three wrong ones.
    boolean pinLockedOut() {
        return getInvalidPinCount() >= 3 && getLastInvalidPin() + 300000 > System.currentTimeMillis();
    }

    void countInvalidPin() {
        setInvalidPinCount(getInvalidPinCount() + 1);
        setLastInvalidPin(System.currentTimeMillis());
    }

    String getEncryptedPassword() {
        return prefs.getString(ENCRYPTED_PASSWORD_KEY, "");
    }

    String getFingerprintIv() {
        return prefs.getString(FINGERPRINT_IV_KEY, "");
    }

    // clearLegacyPassphrases forgets what earlier versions kept to unlock the wallet.
    void clearLegacyPassphrases() {
        prefs.edit()
                .remove(ENCRYPTED_PASSWORD_KEY)
                .remove(FINGERPRINT_IV_KEY)
                .remove(INVALID_PIN_COUNT_KEY)
                .remove(LAST_INVALID_PIN_KEY)
                .apply();
    }

    // The wallet's private passphrase, sealed with WalletSecret.
    void setSealedPassphrase(String sealed) {
        putString(SEALED_PASSPHRASE_KEY, sealed);
    }

    String getSealedPassphrase() {
        return prefs.getString(SEALED_PASSPHRASE_KEY, "");
    }

    void setLastNotification(long timestamp) {
        appPrefs.edit().putLong(LAST_NOTIFICATION_KEY, timestamp).apply();
    }

    long getLastNotification() {
        return appPrefs.getLong(LAST_NOTIFICATION_KEY, 0);
    }

    void setBackupReminder(boolean reminded) {
        putBoolean(BACKUP_REMINDER_KEY, reminded);
    }

    Boolean getBackupReminder() {
        return prefs.getBoolean(BACKUP_REMINDER_KEY, false);
    }

    public void setWalletBirthday(long timestamp) {
        putLong(WALLET_BIRTHDAY_KEY, timestamp);
    }

    public long getWalletBirthday() {
        return prefs.getLong(WALLET_BIRTHDAY_KEY, 0);
    }

    void setRepoVersion(int version) {
        appPrefs.edit().putInt(REPO_VERSION_KEY, version).apply();
    }

    int getRepoVersion() {
        return appPrefs.getInt(REPO_VERSION_KEY, 2);
    }

    void setScanHeight(int scanHeight) {
        putInt(SCAN_HEIGHT_KEY, scanHeight);
    }

    int getScanHeight() {
        return prefs.getInt(SCAN_HEIGHT_KEY, 0);
    }

    void setPeers(String peers) {
        putString(PEERS_KEY, peers);
    }

    String getPeers() {
        return prefs.getString(PEERS_KEY, "");
    }

    /**
     * The peers to sync from in SPV mode, host or host:port, as entered separated by commas or
     * spaces. Entries with characters a host and port can't have are left out.
     */
    String[] getPeerList() {
        return parsePeers(getPeers());
    }

    static String[] parsePeers(String text) {
        java.util.List<String> peers = new java.util.ArrayList<>();
        for (String peer : text.split("[,\\s]+")) {
            if (peer.matches("[A-Za-z0-9.:\\[\\]-]+")) {
                peers.add(peer);
            }
        }
        return peers.toArray(new String[0]);
    }

    void setPeersOnly(boolean only) {
        putBoolean(PEERS_ONLY_KEY, only);
    }

    Boolean getPeersOnly() {
        return prefs.getBoolean(PEERS_ONLY_KEY, false);
    }

    // The nodes on the local network, joined by commas, that the user was last asked to let the
    // app reach.
    void setLocalNetworkAsked(String nodes) {
        putString(LOCAL_NETWORK_ASKED_KEY, nodes);
    }

    String getLocalNetworkAsked() {
        return prefs.getString(LOCAL_NETWORK_ASKED_KEY, "");
    }

    void setNotificationsRequested(boolean requested) {
        appPrefs.edit().putBoolean(NOTIFICATIONS_REQUESTED_KEY, requested).apply();
    }

    Boolean getNotificationsRequested() {
        return appPrefs.getBoolean(NOTIFICATIONS_REQUESTED_KEY, false);
    }

    private void putBoolean(final String key, final boolean val) {
        prefs.edit().putBoolean(key, val).apply();
    }

    private void putInt(final String key, final int val) {
        prefs.edit().putInt(key, val).apply();
    }

    private void putLong(final String key, final long val) {
        prefs.edit().putLong(key, val).apply();
    }

    private void putString(final String key, final String val) {
        prefs.edit().putString(key, val).apply();
    }
}
