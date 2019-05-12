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
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putBoolean(INITIALIZED_KEY, initialized);
        editor.apply();
    }

    Boolean getWalletInitialized() {
        return prefs.getBoolean(INITIALIZED_KEY, false);
    }

    public void setMnemonic(String mnemonic) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putString(MNEMONIC_KEY, mnemonic);
        editor.apply();
    }

    public String getMnemonic() {
        return prefs.getString(MNEMONIC_KEY, "");
    }

    void setBlocksOnly(boolean blocksOnly) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putBoolean(BLOCKS_ONLY_KEY, blocksOnly);
        editor.apply();
    }

    Boolean getBlocksOnly() {
        return prefs.getBoolean(BLOCKS_ONLY_KEY, false);
    }

    void setLastBalance(long balance) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putLong(LAST_BALANCE_KEY, balance);
        editor.apply();
    }

    long getLastBalance() {
        return prefs.getLong(LAST_BALANCE_KEY, 0);
    }

    void setFiatCurrency(String currency) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putString(FIAT_CURRENCY_KEY, currency);
        editor.apply();
    }

    String getFiatCurrency() {
        return prefs.getString(FIAT_CURRENCY_KEY, "usd");
    }

    void setLastAddress(String addr) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putString(LAST_ADDRESS_KEY, addr);
        editor.apply();
    }

    String getLastAddress() {
        return prefs.getString(LAST_ADDRESS_KEY, "");
    }

    void setLastBlockHeight(int blockHeight) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putInt(LAST_BLOCK_HEIGHT_KEY, blockHeight);
        editor.apply();
    }

    int getLastBlockHeight() {
        return prefs.getInt(LAST_BLOCK_HEIGHT_KEY, 0);
    }

    void setLastBlockHash(String blockHash) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putString(LAST_BLOCK_HASH_KEY, blockHash);
        editor.apply();
    }

    String getLastBlockHash() {
        return prefs.getString(LAST_BLOCK_HASH_KEY, "");
    }

    void setFeePerByte(int satPerByte) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putInt(FEE_PER_BYTE_KEY, satPerByte);
        editor.apply();
    }

    int getFeePerByte() {
        return prefs.getInt(FEE_PER_BYTE_KEY, 15);
    }

    void setDefaultLabel(String label) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putString(DEFAULT_LABEL_KEY, label);
        editor.apply();
    }

    String getDefaultLabel() {
        return prefs.getString(DEFAULT_LABEL_KEY, "");
    }

    void setDefaultMemo(String memo) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putString(DEFAULT_MEMO_KEY, memo);
        editor.apply();
    }

    String getDefaultMemo() {
        return prefs.getString(DEFAULT_MEMO_KEY, "");
    }

    void setBchdIP(String ip) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putString(BCHD_IP_KEY, ip);
        editor.apply();
    }

    String getBchdIP() {
        return prefs.getString(BCHD_IP_KEY, "");
    }

    void setBchdUsername(String username) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putString(BCHD_USERNAME_KEY, username);
        editor.apply();
    }

    String getBchdUsername() {
        return prefs.getString(BCHD_USERNAME_KEY, "");
    }

    void setBchdPassword(String password) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putString(BCHD_PASSWORD_KEY, password);
        editor.apply();
    }

    String getBchdPassword() {
        return prefs.getString(BCHD_PASSWORD_KEY, "");
    }

    void setBchdCert(String cert) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putString(BCHD_CERT_KEY, cert);
        editor.apply();
    }

    String getBchdCert() {
        return prefs.getString(BCHD_CERT_KEY, "");
    }

    void setEncryptionType(EncryptionType et) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putString(ENCRYPTION_TYPE_KEY, et.toString());
        editor.apply();
    }

    EncryptionType getEncryptionType() {
        String t = prefs.getString(ENCRYPTION_TYPE_KEY, "unencrypted");
        return EncryptionType.fromString(t);
    }

    void setInvalidPinCount(int count) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putInt(INVALID_PIN_COUNT_KEY, count);
        editor.apply();
    }

    int getInvalidPinCount() {
        return prefs.getInt(INVALID_PIN_COUNT_KEY, 0);
    }

    void setLastInvalidPin(long timestamp) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putLong(LAST_INVALID_PIN_KEY, timestamp);
        editor.apply();
    }

    long getLastInvalidPin() {
        return prefs.getLong(LAST_INVALID_PIN_KEY, 0);
    }

    void setEncryptedPassword(String pw) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putString(ENCRYPTED_PASSWORD_KEY, pw);
        editor.apply();
    }

    String getEncryptedPassword() {
        return prefs.getString(ENCRYPTED_PASSWORD_KEY, "");
    }

    void setFingerprintIv(String iv) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putString(FINGERPRINT_IV_KEY, iv);
        editor.apply();
    }

    String getFingerprintIv() {
        return prefs.getString(FINGERPRINT_IV_KEY, "");
    }

    void setLastNotification(long timestamp) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putLong(LAST_NOTIFICATION_KEY, timestamp);
        editor.apply();
    }

    long getLastNotification() {
        return prefs.getLong(LAST_NOTIFICATION_KEY, 0);
    }

    void setBackupReminder(boolean reminded) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putBoolean(BACKUP_REMINDER_KEY, reminded);
        editor.apply();
    }

    Boolean getBackupReminder() {
        return prefs.getBoolean(BACKUP_REMINDER_KEY, false);
    }

    public void setWalletBirthday(long timestamp) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putLong(WALLET_BIRTHDAY_KEY, timestamp);
        editor.apply();
    }

    public long getWalletBirthday() {
        return prefs.getLong(WALLET_BIRTHDAY_KEY, 0);
    }

    void setRepoVersion(int version) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putInt(REPO_VERSION_KEY, version);
        editor.apply();
    }

    int getRepoVersion() {
        return prefs.getInt(REPO_VERSION_KEY, 0);
    }
}
