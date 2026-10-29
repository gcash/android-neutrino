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
     * prompt returns a prompt for the screen lock that tells the activity's callback, which the
     * activity creates in onCreate. The prompt outlives the activity when Android recreates it,
     * as for a configuration change while the prompt shows, and tells the recreated activity,
     * whose callback replaces the one before.
     */
    static Prompt prompt(FragmentActivity activity, Callback callback) {
        return new Prompt(activity, callback);
    }

    static final class Prompt {
        private final FragmentActivity activity;
        private final Callback callback;
        private final BiometricPrompt prompt;

        private Prompt(FragmentActivity activity, Callback callback) {
            this.activity = activity;
            this.callback = callback;
            prompt = new BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
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
        }

        /**
         * confirm asks for the screen lock, if the phone has one, and calls the callback on the
         * UI thread once the user confirmed or cancelled.
         */
        void confirm(CharSequence title) {
            if (!isSet(activity)) {
                callback.onConfirmed();
                return;
            }
            prompt.authenticate(new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setAllowedAuthenticators(AUTHENTICATORS)
                    .build());
        }
    }
}
