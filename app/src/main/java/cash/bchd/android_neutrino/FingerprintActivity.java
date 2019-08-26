package cash.bchd.android_neutrino;

import android.annotation.TargetApi;
import android.app.KeyguardManager;
import android.content.pm.PackageManager;
import android.hardware.fingerprint.FingerprintManager;
import android.Manifest;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class FingerprintActivity extends AppCompatActivity {

    // Declare a string variable for the key we’re going to use in our fingerprint authentication
    private static final String KEY_NAME = "NeutrinoKey";
    private static final String KEYSTORE = "AndroidKeyStore";


    private KeyStore keyStore;
    private KeyGenerator generator;
    private Cipher cipher;
    protected FingerprintManager fingerprintManager;
    private KeyguardManager keyguardManager;
    protected FingerprintManager.CryptoObject cryptoObject;

    protected void initFingerprintScanner(boolean forEncryption) throws FingerprintException {
        // If you’ve set your app’s minSdkVersion to anything lower than 23, then you’ll need to verify that the device is running Marshmallow
        // or higher before executing any fingerprint-related code
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //Get an instance of KeyguardManager and FingerprintManager//
            keyguardManager =
                    (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            fingerprintManager =
                    (FingerprintManager) getSystemService(FINGERPRINT_SERVICE);

            //Check whether the device has a fingerprint sensor//
            if (!fingerprintManager.isHardwareDetected()) {
                // If a fingerprint sensor isn’t available, then inform the user that they’ll be unable to use your app’s fingerprint functionality//
                throw new FingerprintException("Your device doesn't support fingerprint authentication");
            }
            //Check whether the user has granted your app the USE_FINGERPRINT permission//
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.USE_FINGERPRINT) != PackageManager.PERMISSION_GRANTED) {
                // If your app doesn't have this permission, then display the following text//
                throw new FingerprintException("Please enable the fingerprint permission");
            }

            //Check that the user has registered at least one fingerprint//
            if (!fingerprintManager.hasEnrolledFingerprints()) {
                // If the user hasn’t configured any fingerprints, then display the following message//
                throw new FingerprintException("No fingerprint configured. Please register at least one fingerprint in your device's Settings");
            }

            //Check that the lockscreen is secured//
            if (!keyguardManager.isKeyguardSecure()) {
                // If the user hasn’t secured their lockscreen with a PIN password or pattern, then display the following text//
                throw new FingerprintException("Please enable lockscreen security in your device's Settings");
            } else {
                try {
                    getKeyStore();
                    createNewKey(false);
                    getCipher();

                    int mode = Cipher.ENCRYPT_MODE;
                    if (!forEncryption) {
                        mode = Cipher.DECRYPT_MODE;
                    }
                    initCipher(mode);

                    initCryptObject();
                } catch (Exception e) {
                    throw new FingerprintException("Error initializing cipher");
                }
            }
        }
    }

    private void getKeyStore() throws Exception {
        keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
    }

    @TargetApi(Build.VERSION_CODES.M)
    public boolean createNewKey(boolean forceCreate) throws Exception {
        if (forceCreate)
            keyStore.deleteEntry(KEY_NAME);

        if (!keyStore.containsAlias(KEY_NAME)) {
            generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);

            generator.init(new KeyGenParameterSpec.Builder(KEY_NAME,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .setUserAuthenticationRequired(true)
                    .build()
            );

            generator.generateKey();
        } else
            return true;

        return false;
    }

    private void getCipher() throws Exception {
        cipher = Cipher.getInstance(
                KeyProperties.KEY_ALGORITHM_AES + "/"
                        + KeyProperties.BLOCK_MODE_CBC + "/"
                        + KeyProperties.ENCRYPTION_PADDING_PKCS7);
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void initCipher(int mode) throws Exception {
        try {
            keyStore.load(null);
            SecretKey keyspec = (SecretKey)keyStore.getKey(KEY_NAME, null);

            if (mode == Cipher.ENCRYPT_MODE) {
                cipher.init(mode, keyspec);

                Settings.getInstance().setFingerprintIv(Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP));
            } else {
                byte[] iv = Base64.decode(Settings.getInstance().getFingerprintIv(), Base64.NO_WRAP);
                IvParameterSpec ivspec = new IvParameterSpec(iv);
                cipher.init(mode, keyspec, ivspec);
            }
        } catch (KeyPermanentlyInvalidatedException e) {
            createNewKey(true); // Retry after clearing entry
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void initCryptObject() throws Exception {
        cryptoObject = new FingerprintManager.CryptoObject(cipher);
    }

    private class FingerprintException extends Exception {
        public FingerprintException(String s) {
            super(new Exception(s));
        }
    }
}