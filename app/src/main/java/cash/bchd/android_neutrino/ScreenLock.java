package cash.bchd.android_neutrino;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

/**
 * ScreenLock asks for the phone's screen lock, a fingerprint or face, or the device PIN, pattern
 * or password, before the app uses the wallet's passphrase. Android counts and limits the failed
 * attempts. Without a screen lock there is nothing to ask for, and the wallet works as before.
 */
final class ScreenLock {

    // The combination that androidx.biometric supports on every Android version the app runs on.
    private static final int AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK
            | BiometricManager.Authenticators.DEVICE_CREDENTIAL;

    interface Callback {
        void onConfirmed();

        void onCancelled();
    }

    private ScreenLock() {
    }

    /**
     * isSet returns whether the phone has a screen lock to ask for.
     */
    static boolean isSet(Context context) {
        return BiometricManager.from(context).canAuthenticate(AUTHENTICATORS)
                == BiometricManager.BIOMETRIC_SUCCESS;
    }

    /**
     * confirm asks for the screen lock, if the phone has one, and calls the callback on the UI
     * thread once the user confirmed or cancelled.
     */
    static void confirm(FragmentActivity activity, CharSequence title, Callback callback) {
        if (!isSet(activity)) {
            callback.onConfirmed();
            return;
        }
        BiometricPrompt prompt = new BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        callback.onConfirmed();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        callback.onCancelled();
                    }
                });
        prompt.authenticate(new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build());
    }
}
