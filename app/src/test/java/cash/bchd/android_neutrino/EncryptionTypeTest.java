package cash.bchd.android_neutrino;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;

public class EncryptionTypeTest {

    @Test
    public void readsTypesInCapitalsInTurkish() {
        // Where a capital I lowercases to a dotless i.
        Locale saved = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            assertEquals(EncryptionType.PIN, EncryptionType.fromString("PIN"));
            assertEquals(EncryptionType.FINGERPRINT, EncryptionType.fromString("FINGERPRINT"));
        } finally {
            Locale.setDefault(saved);
        }
    }
}
