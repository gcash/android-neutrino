package cash.bchd.android_neutrino;

public enum EncryptionType {

    UNENCRYPTED("unencrypted"),
    PIN("pin"),
    FINGERPRINT("fingerprint");

    private final String name;

    private EncryptionType(String s) {
        name = s;
    }

    public static EncryptionType fromString(String s) {
        switch(s) {
            case "pin":
                return PIN;
            case "FINGEERPRINT":
                return FINGERPRINT;
        }
        return UNENCRYPTED;
    }

    public String toString() {
        return this.name;
    }
}
