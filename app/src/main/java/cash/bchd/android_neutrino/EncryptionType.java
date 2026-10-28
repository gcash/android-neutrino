package cash.bchd.android_neutrino;

import java.util.Locale;

/**
 * EncryptionType is how the wallet's private passphrase is kept. KEYSTORE is a random passphrase
 * sealed with WalletSecret. The others are what earlier versions used, which the app replaces with
 * KEYSTORE: the default passphrase, a hash of a PIN, or a random passphrase sealed with a key
 * that needs a fingerprint.
 */
public enum EncryptionType {

    UNENCRYPTED("unencrypted"),
    PIN("pin"),
    FINGERPRINT("fingerprint"),
    KEYSTORE("keystore");

    private final String name;

    private EncryptionType(String s) {
        name = s;
    }

    public static EncryptionType fromString(String s) {
        switch(s.toLowerCase(Locale.ROOT)) {
            case "pin":
                return PIN;
            case "fingerprint":
                return FINGERPRINT;
            case "keystore":
                return KEYSTORE;
        }
        return UNENCRYPTED;
    }

    public String toString() {
        return this.name;
    }
}
