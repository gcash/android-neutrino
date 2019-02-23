package cash.bchd.android_neutrino;

import android.app.Activity;
import android.support.v7.app.AppCompatActivity;

import cash.bchd.android_neutrino.wallet.Wallet;

public class CloseActivity extends AppCompatActivity {
    protected static Wallet wallet;
    @Override
    protected void onDestroy() {
        wallet.stop();
        super.onDestroy();
        wallet = null;
    }
}
