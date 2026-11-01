package cash.bchd.android_neutrino;

import androidx.appcompat.app.AppCompatActivity;

import cash.bchd.android_neutrino.wallet.Wallet;

/**
 * CloseActivity stops the wallet when the user leaves the app from the main screen, which
 * extends it, by going back from the bottom of the app's screens. It keeps the wallet running
 * when the screen is only recreated, such as after the screen rotated, and when a main screen
 * that was opened on top of another one closes, as the one below still shows the wallet. The
 * wallet also stops once the app has been in the background for ten minutes, which WalletCloser
 * counts for the whole app.
 */
public class CloseActivity extends AppCompatActivity {

    // Whether the screen is at the bottom of the app's screens, noted while it's in front, as a
    // screen that goes back from there is already out of them when it pauses.
    private boolean bottom;

    @Override
    protected void onResume() {
        super.onResume();
        bottom = isTaskRoot();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing() && bottom) {
            Wallet wallet = Wallet.getInstance();
            if (wallet != null) {
                wallet.stop();
            }
        }
    }
}
