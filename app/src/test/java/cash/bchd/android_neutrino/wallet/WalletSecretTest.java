package cash.bchd.android_neutrino.wallet;

import com.google.common.io.BaseEncoding;

import org.junit.Test;

import java.security.GeneralSecurityException;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class WalletSecretTest {

    // A software key in place of the Keystore's, which only Android has.
    private static SecretKey aesKey() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        return generator.generateKey();
    }

    @Test
    public void makesRandomPassphrasesOf32Bytes() {
        String passphrase = WalletSecret.newPassphrase();
        assertTrue(passphrase.matches("[0-9a-f]{64}"));
        assertNotEquals(passphrase, WalletSecret.newPassphrase());
    }

    @Test
    public void opensWhatItSealed() throws Exception {
        SecretKey key = aesKey();
        String passphrase = WalletSecret.newPassphrase();
        String sealed = WalletSecret.seal(key, passphrase);
        assertEquals(passphrase, WalletSecret.open(key, sealed));
        // Each sealing has its own nonce.
        assertNotEquals(sealed, WalletSecret.seal(key, passphrase));
    }

    @Test
    public void refusesAnotherKeyOrChangedBytes() throws Exception {
        SecretKey key = aesKey();
        String sealed = WalletSecret.seal(key, WalletSecret.newPassphrase());
        assertRefused(aesKey(), sealed);

        byte[] bytes = BaseEncoding.base64().decode(sealed);
        bytes[bytes.length - 1] ^= 1;
        assertRefused(key, BaseEncoding.base64().encode(bytes));
        assertRefused(key, "not base64!");
        assertRefused(key, BaseEncoding.base64().encode(new byte[12]));
    }

    private static void assertRefused(SecretKey key, String sealed) {
        try {
            WalletSecret.open(key, sealed);
            fail("opened " + sealed);
        } catch (GeneralSecurityException e) {
            // Expected.
        }
    }
}
