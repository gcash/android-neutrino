package cash.bchd.android_neutrino;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * Vibrations buzzes the phone when a payment arrives or goes out, a QR code is scanned, a
 * recovery phrase is backed up or a wallet is restored.
 */
final class Vibrations {

    private static final long BUZZ_MILLIS = 500;

    private Vibrations() {
    }

    /** buzz vibrates for half a second, on a phone that has a vibrator. */
    static void buzz(Context context) {
        Vibrator vibrator = context.getSystemService(Vibrator.class);
        if (vibrator == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(BUZZ_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            buzzWithoutEffect(vibrator);
        }
    }

    // VibrationEffect came with Android 8, which deprecated this call.
    @SuppressWarnings("deprecation")
    private static void buzzWithoutEffect(Vibrator vibrator) {
        vibrator.vibrate(BUZZ_MILLIS);
    }
}
