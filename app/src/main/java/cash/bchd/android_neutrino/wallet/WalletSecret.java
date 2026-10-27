package cash.bchd.android_neutrino.wallet;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import com.google.common.io.BaseEncoding;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * WalletSecret makes the wallet's private passphrase, a random secret, and seals it, and the
 * recovery phrase until the user backed it up, with an AES key in the Android Keystore, which
 * can't be read out of the phone. The key needs no authentication, so removing the screen lock
 * doesn't make Android erase it; the app asks for the screen lock itself before it uses them.
 */
public final class WalletSecret {

    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "NeutrinoWalletPassphrase";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private WalletSecret() {
    }

    /**
     * newPassphrase returns a random passphrase of 32 bytes, in hex.
     */
    public static String newPassphrase() {
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        return BaseEncoding.base16().lowerCase().encode(secret);
    }

    /**
     * seal encrypts the passphrase with the app's Keystore key, which it creates the first time.
     */
    public static String seal(String passphrase) throws GeneralSecurityException, IOException {
        return seal(key(), passphrase);
    }

    /**
     * open decrypts a passphrase that seal returned.
     */
    public static String open(String sealed) throws GeneralSecurityException, IOException {
        return open(key(), sealed);
    }

    static String seal(SecretKey key, String passphrase) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(passphrase.getBytes(StandardCharsets.UTF_8));
        byte[] sealed = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, sealed, 0, iv.length);
        System.arraycopy(ciphertext, 0, sealed, iv.length, ciphertext.length);
        return BaseEncoding.base64().encode(sealed);
    }

    static String open(SecretKey key, String sealed) throws GeneralSecurityException {
        byte[] bytes;
        try {
            bytes = BaseEncoding.base64().decode(sealed);
        } catch (IllegalArgumentException e) {
            throw new GeneralSecurityException("The sealed passphrase is not base64", e);
        }
        if (bytes.length <= IV_BYTES) {
            throw new GeneralSecurityException("The sealed passphrase is too short");
        }
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES));
        byte[] plaintext = cipher.doFinal(bytes, IV_BYTES, bytes.length - IV_BYTES);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    private static SecretKey key() throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        Key key = keyStore.getKey(KEY_ALIAS, null);
        if (key instanceof SecretKey) {
            return (SecretKey) key;
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }
}
