package cash.bchd.android_neutrino;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.widget.TextView;

import java.util.Currency;

import cash.bchd.android_neutrino.wallet.Amount;
import cash.bchd.android_neutrino.wallet.ExchangeRates;
import cash.bchd.android_neutrino.wallet.Wallet;

public class SendActivity extends AppCompatActivity {

    Wallet wallet;
    TextView balanceTxtView;
    Amount balance;
    String fiatCurrency;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send);

        CloseActivity.cancelCloseTimer();

        Intent intent = getIntent();
        fiatCurrency = intent.getExtras().getString("fiatCurrency");

        wallet = Wallet.getInstance();


        Toolbar myToolbar = (Toolbar) findViewById(R.id.sendToolbar);
        setSupportActionBar(myToolbar);

        getSupportActionBar().setDisplayShowTitleEnabled(false);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        balanceTxtView = (TextView) findViewById(R.id.sendBalance);
        try {
            balance = new Amount(wallet.balance());
            String balanceStr = "Balance: " + ExchangeRates.getInstance().getFormattedAmountInFiat(balance, Currency.getInstance(fiatCurrency));
            balanceTxtView.setText(balanceStr);

        } catch(Exception e) {
            e.printStackTrace();
        }

    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:

                Intent intent = new Intent(this, MainActivity.class);
                startActivity(intent);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }
}