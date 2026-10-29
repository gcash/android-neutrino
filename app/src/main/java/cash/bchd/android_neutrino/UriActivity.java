package cash.bchd.android_neutrino;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.io.InputStream;

import cash.bchd.android_neutrino.wallet.Wallet;

public class UriActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        Uri uri = intent.getData();
        boolean paymentRequest = PaymentRequestFile.MIME_TYPE.equals(intent.getType());
        if (uri != null && paymentRequest && "content".equals(uri.getScheme())) {
            // A payment request that a browser downloaded, which is read off the UI thread,
            // since its provider may read it from storage, while the permission to read it,
            // which ends with this screen, lasts.
            readPaymentRequest(uri);
            return;
        }
        if (uri != null) {
            String uriString = intent.getDataString();
            if (paymentRequest) {
                // A browser that hands over the link to a payment request, rather than the
                // file, gets it downloaded as for a payment link.
                uriString = Settings.get(this).getNetwork().uriPrefix() + "?r=" + Uri.encode(uriString);
            }

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

    // readPaymentRequest reads the payment request at uri in the background and opens it, or
    // says why it can't, and then closes this screen.
    private void readPaymentRequest(Uri uri) {
        new Thread(() -> {
            byte[] request = null;
            int problem = R.string.payment_request_unreadable;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in != null) {
                    request = PaymentRequestFile.read(in);
                }
            } catch (PaymentRequestFile.TooLargeException e) {
                problem = R.string.payment_request_too_large;
            } catch (IOException | SecurityException e) {
                e.printStackTrace();
            }
            byte[] read = request;
            int message = problem;
            runOnUiThread(() -> {
                if (read != null) {
                    openPaymentRequest(read);
                } else {
                    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                }
                finish();
            });
        }).start();
    }

    // openPaymentRequest opens the screen that pays a payment request: the send screen, or the
    // main screen when the wallet has yet to start, which it then does first.
    private void openPaymentRequest(byte[] request) {
        Intent newIntent;
        if (Wallet.getInstance() == null) {
            newIntent = new Intent(this, MainActivity.class);
        } else {
            newIntent = new Intent(this, SendActivity.class);
            newIntent.putExtra("fiatCurrency", Settings.getInstance().getFiatCurrency());
            newIntent.putExtra("feePerByte", Settings.getInstance().getFeePerByte());
        }
        newIntent.putExtra(PaymentRequestFile.EXTRA, request);
        startActivity(newIntent);
    }
}
