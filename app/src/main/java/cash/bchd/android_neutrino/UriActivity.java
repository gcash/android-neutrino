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
                newIntent.putExtra("fiatCurrenty", Settings.getInstance().getFiatCurrency());
                newIntent.putExtra("feePerByte", Settings.getInstance().getFeePerByte());
                startActivity(newIntent);
            }
        }
    }
}