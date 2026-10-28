package cash.bchd.android_neutrino;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import cash.bchd.android_neutrino.wallet.Wallet;

public class UriActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        Uri uri = intent.getData();
        if (uri != null) {
            String uriString = intent.getDataString();

            if (Wallet.getInstance() == null) {
                Intent newIntent = new Intent(this, MainActivity.class);
                newIntent.putExtra("uri", uriString);
                startActivity(newIntent);
            } else {
                Intent newIntent = new Intent(this, SendActivity.class);
                newIntent.putExtra("qrdata", uriString);
                newIntent.putExtra("fiatCurrency", Settings.getInstance().getFiatCurrency());
                newIntent.putExtra("feePerByte", Settings.getInstance().getFeePerByte());
                startActivity(newIntent);
            }
        }
        // It has no screen of its own, so it closes once it started the one that handles the
        // URI, instead of staying behind it as a blank screen at the bottom of the task. There
        // the next link, which a browser opens with FLAG_ACTIVITY_CLEAR_TOP, cleared the task,
        // and with it the main screen, which stopped the wallet.
        finish();
    }
}
