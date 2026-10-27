package cash.bchd.android_neutrino;

import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.common.io.BaseEncoding;

import java.security.Key;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;

/**
 * LegacyFingerprint reads the passphrase that earlier versions sealed with a Keystore key that
 * needs a fingerprint, so that PassphraseUpgrade can replace it. Android erases such a key when
 * the screen lock or the fingerprints change, which leaves only the recovery phrase.
 */
final class LegacyFingerprint {

    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "NeutrinoKey";

    interface Callback {
        void onPassphrase(String passphrase);

        void onFailed(CharSequence message);
    }

    private LegacyFingerprint() {
    }

    /**
     * read asks for a fingerprint and calls the callback on the UI thread with the passphrase it
     * unsealed, or why it couldn't.
     */
    static void read(FragmentActivity activity, Settings settings, Callback callback) {
        Cipher cipher;
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
            keyStore.load(null);
            Key key = keyStore.getKey(KEY_ALIAS, null);
            if (key == null) {
                callback.onFailed(activity.getString(R.string.fingerprint_key_gone));
                return;
            }
            cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
            byte[] iv = Base64.decode(settings.getFingerprintIv(), Base64.NO_WRAP);
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
        } catch (KeyPermanentlyInvalidatedException e) {
            callback.onFailed(activity.getString(R.string.fingerprint_key_gone));
            return;
        } catch (Exception e) {
            callback.onFailed(e.toString());
            return;
        }

        BiometricPrompt prompt = new BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        try {
                            byte[] encrypted = BaseEncoding.base16().lowerCase().decode(settings.getEncryptedPassword());
                            byte[] passphrase = result.getCryptoObject().getCipher().doFinal(encrypted);
                            callback.onPassphrase(BaseEncoding.base16().lowerCase().encode(passphrase));
                        } catch (Exception e) {
                            callback.onFailed(e.toString());
                        }
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        callback.onFailed(errString);
                    }
                });
        prompt.authenticate(new BiometricPrompt.PromptInfo.Builder()
                        .setTitle(activity.getString(R.string.fingerprint_prompt_title))
                        .setNegativeButtonText(activity.getString(android.R.string.cancel))
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                        .build(),
                new BiometricPrompt.CryptoObject(cipher));
    }

    /**
     * deleteKey deletes the fingerprint key once the wallet no longer needs it.
     */
    static void deleteKey() {
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
            keyStore.load(null);
            keyStore.deleteEntry(KEY_ALIAS);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
