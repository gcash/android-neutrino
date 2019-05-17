package cash.bchd.android_neutrino;

import android.content.SharedPreferences;

public class Settings {

    private static final String INITIALIZED_KEY = "Initialized";
    private static final String MNEMONIC_KEY = "Mnemonic";
    private static final String BLOCKS_ONLY_KEY = "BlocksOnly";
    private static final String LAST_BALANCE_KEY = "LastBalance";
    private static final String FIAT_CURRENCY_KEY = "FiatCurrency";
    private static final String LAST_ADDRESS_KEY = "LastAddress";
    private static final String LAST_BLOCK_HEIGHT_KEY = "LastBlockHeight";
    private static final String LAST_BLOCK_HASH_KEY = "LastBlockHash";
    private static final String FEE_PER_BYTE_KEY = "FeePerByte";
    private static final String DEFAULT_LABEL_KEY = "DefaultLabel";
    private static final String DEFAULT_MEMO_KEY = "DefaultMemo";
    private static final String BCHD_IP_KEY = "BchdIP";
    private static final String BCHD_USERNAME_KEY = "BchdUsername";
    private static final String BCHD_PASSWORD_KEY = "BchdPassword";
    private static final String BCHD_CERT_KEY = "BchdCert";
    private static final String ENCRYPTION_TYPE_KEY = "EncryptionType";
    private static final String INVALID_PIN_COUNT_KEY = "InvalidPinCount";
    private static final String LAST_INVALID_PIN_KEY = "LastInvalidPin";
    private static final String ENCRYPTED_PASSWORD_KEY = "EncryptedPassword";
    private static final String FINGERPRINT_IV_KEY = "FingerprintIV";
    private static final String LAST_NOTIFICATION_KEY = "LastNotification";
    private static final String BACKUP_REMINDER_KEY = "BackupReminder";
    private static final String WALLET_BIRTHDAY_KEY = "WalletBirthday";
    private static final String REPO_VERSION_KEY = "RepoVersion";

    private final SharedPreferences prefs;

    private static Settings instance;

    Settings(SharedPreferences prefs) {
        this.prefs = prefs;
        instance = this;
    }

    public static Settings getInstance() {
        return instance;
    }

    void setWalletInitialized(boolean initialized) {
        putBoolean(INITIALIZED_KEY, initialized);
    }

    Boolean getWalletInitialized() {
        return prefs.getBoolean(INITIALIZED_KEY, false);
    }

    public void setMnemonic(String mnemonic) {
        putString(MNEMONIC_KEY, mnemonic);
    }

    public String getMnemonic() {
        return prefs.getString(MNEMONIC_KEY, "");
    }

    void setBlocksOnly(boolean blocksOnly) {
        putBoolean(BLOCKS_ONLY_KEY, blocksOnly);
    }

    Boolean getBlocksOnly() {
        return prefs.getBoolean(BLOCKS_ONLY_KEY, false);
    }

    void setLastBalance(long balance) {
        putLong(LAST_BALANCE_KEY, balance);
    }

    long getLastBalance() {
        return prefs.getLong(LAST_BALANCE_KEY, 0);
    }

    void setFiatCurrency(String currency) {
        putString(FIAT_CURRENCY_KEY, currency);
    }

    String getFiatCurrency() {
        return prefs.getString(FIAT_CURRENCY_KEY, "usd");
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
        putInt(FEE_PER_BYTE_KEY, satPerByte);
    }

    int getFeePerByte() {
        return prefs.getInt(FEE_PER_BYTE_KEY, 15);
    }

    void setDefaultLabel(String label) {
        putString(DEFAULT_LABEL_KEY, label);
    }

    String getDefaultLabel() {
        return prefs.getString(DEFAULT_LABEL_KEY, "");
    }

    void setDefaultMemo(String memo) {
        putString(DEFAULT_MEMO_KEY, memo);
    }

    String getDefaultMemo() {
        return prefs.getString(DEFAULT_MEMO_KEY, "");
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

    void setEncryptedPassword(String pw) {
        putString(ENCRYPTED_PASSWORD_KEY, pw);
    }

    String getEncryptedPassword() {
        return prefs.getString(ENCRYPTED_PASSWORD_KEY, "");
    }

    void setFingerprintIv(String iv) {
        putString(FINGERPRINT_IV_KEY, iv);
    }

    String getFingerprintIv() {
        return prefs.getString(FINGERPRINT_IV_KEY, "");
    }

    void setLastNotification(long timestamp) {
        putLong(LAST_NOTIFICATION_KEY, timestamp);
    }

    long getLastNotification() {
        return prefs.getLong(LAST_NOTIFICATION_KEY, 0);
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
        putInt(REPO_VERSION_KEY, version);
    }

    int getRepoVersion() {
        return prefs.getInt(REPO_VERSION_KEY, 0);
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
