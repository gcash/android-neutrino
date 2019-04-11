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

    SharedPreferences prefs;

    private static Settings instance;

    Settings(SharedPreferences prefs) {
        this.prefs = prefs;
        instance = this;
    }

    public static Settings getInstance() {
        return instance;
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

    public void setLastBlockHeight(int blockHeight) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putInt(LAST_BLOCK_HEIGHT_KEY, blockHeight);
        editor.apply();
    }

    public int getLastBlockHeight() {
        int height = prefs.getInt(LAST_BLOCK_HEIGHT_KEY, 0);
        return height;
    }

    public void setLastBlockHash(String blockHash) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putString(LAST_BLOCK_HASH_KEY, blockHash);
        editor.apply();
    }

    public String getLastBlockHash() {
        String hash = prefs.getString(LAST_BLOCK_HASH_KEY, "");
        return hash;
    }

    public void setFeePerByte(int satPerByte) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putInt(FEE_PER_BYTE_KEY, satPerByte);
        editor.apply();
    }

    public int getFeePerByte() {
        int fee = prefs.getInt(FEE_PER_BYTE_KEY, 15);
        return fee;
    }

    public void setDefaultLabel(String label) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putString(DEFAULT_LABEL_KEY, label);
        editor.apply();
    }

    public String getDefaultLabel() {
        String label = prefs.getString(DEFAULT_LABEL_KEY, "");
        return label;
    }

    public void setDefaultMemo(String memo) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putString(DEFAULT_MEMO_KEY, memo);
        editor.apply();
    }

    public String getDefaultMemo() {
        String memo = prefs.getString(DEFAULT_MEMO_KEY, "");
        return memo;
    }

    public void setBchdIP(String ip) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putString(BCHD_IP_KEY, ip);
        editor.apply();
    }

    public String getBchdIP() {
        String ip = prefs.getString(BCHD_IP_KEY, "");
        return ip;
    }

    public void setBchdUsername(String username) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putString(BCHD_USERNAME_KEY, username);
        editor.apply();
    }

    public String getBchdUsername() {
        String username = prefs.getString(BCHD_USERNAME_KEY, "");
        return username;
    }

    public void setBchdPassword(String password) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putString(BCHD_PASSWORD_KEY, password);
        editor.apply();
    }

    public String getBchdPassword() {
        String password = prefs.getString(BCHD_PASSWORD_KEY, "");
        return password;
    }

    public void setBchdCert(String cert) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putString(BCHD_CERT_KEY, cert);
        editor.apply();
    }

    public String getBchdCert() {
        String cert = prefs.getString(BCHD_CERT_KEY, "");
        return cert;
    }

    public void setEncryptionType(EncryptionType et) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putString(ENCRYPTION_TYPE_KEY, et.toString());
        editor.apply();
    }

    public EncryptionType getEncryptionType() {
        String t = prefs.getString(ENCRYPTION_TYPE_KEY, "unencrypted");
        return EncryptionType.fromString(t);
    }

    public void setInvalidPinCount(int count) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putInt(INVALID_PIN_COUNT_KEY, count);
        editor.apply();
    }

    public int getInvalidPinCount() {
        int count = prefs.getInt(INVALID_PIN_COUNT_KEY, 0);
        return count;
    }

    public void setLastInvalidPin(long timestamp) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putLong(LAST_INVALID_PIN_KEY, timestamp);
        editor.apply();
    }

    public long getLastInvalidPin() {
        long timestamp = prefs.getLong(LAST_INVALID_PIN_KEY, 0);
        return timestamp;
    }

    public void setEncryptedPassword(String pw) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putString(ENCRYPTED_PASSWORD_KEY, pw);
        editor.apply();
    }

    public String getEncryptedPassword() {
        String pw = prefs.getString(ENCRYPTED_PASSWORD_KEY, "");
        return pw;
    }

    public void setFingerprintIv(String iv) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putString(FINGERPRINT_IV_KEY, iv);
        editor.apply();
    }

    public String getFingerprintIv() {
        String iv = prefs.getString(FINGERPRINT_IV_KEY, "");
        return iv;
    }

    public void setLastNotification(long timestamp) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putLong(LAST_NOTIFICATION_KEY, timestamp);
        editor.apply();
    }

    public long getLastNotification() {
        long timestamp = prefs.getLong(LAST_NOTIFICATION_KEY, 0);
        return timestamp;
    }

    public void setBackupReminder(boolean reminded) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putBoolean(BACKUP_REMINDER_KEY, reminded);
        editor.apply();
    }

    public Boolean getBackupReminder() {
        boolean reminded = prefs.getBoolean(BACKUP_REMINDER_KEY, false);
        return reminded;
    }

    public void setWalletBirthday(long timestamp) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putLong(WALLET_BIRTHDAY_KEY, timestamp);
        editor.apply();
    }

    public long getWalletBirthday() {
        long timestamp = prefs.getLong(WALLET_BIRTHDAY_KEY, 0);
        return timestamp;
    }

    public void setRepoVersion(int version) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putInt(REPO_VERSION_KEY, version);
        editor.apply();
    }

    public int getRepoVersion() {
        int version = prefs.getInt(REPO_VERSION_KEY, 0);
        return version;
    }
}
