package cash.bchd.android_neutrino;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.design.widget.TextInputEditText;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ToggleButton;

import org.w3c.dom.Text;

import java.util.Currency;

import cash.bchd.android_neutrino.wallet.Amount;
import cash.bchd.android_neutrino.wallet.ExchangeRates;
import cash.bchd.android_neutrino.wallet.Wallet;
import walletrpc.Api;

public class SendActivity extends AppCompatActivity {

    Wallet wallet;
    TextView balanceTxtView;
    Amount balance;
    String fiatCurrency;
    boolean showingFiat = true;
    TextInputEditText inputAmount;
    TextView conversionRate;
    boolean sendAll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send);

        CloseActivity.cancelCloseTimer();

        Intent intent = getIntent();
        fiatCurrency = intent.getExtras().getString("fiatCurrency");
        int satPerByte = intent.getExtras().getInt("feePerByte");

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

        TextView symbolLabel = (TextView) findViewById(R.id.symbolLabel);

        symbolLabel.setText(Currency.getInstance(fiatCurrency).getSymbol());

        inputAmount = (TextInputEditText) findViewById(R.id.amountInput);
        conversionRate = (TextView) findViewById(R.id.conversionRate);
        inputAmount.addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {}

            @Override
            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateAlternateAmount();
                if (!inputAmount.getText().toString().equals("")) {
                    TextInputEditText etAmount = (TextInputEditText) findViewById(R.id.amountInput);
                    etAmount.setError(null);
                }
            }
        });

        ImageView toggle = (ImageView) findViewById(R.id.toggleImage);
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (showingFiat) {
                    String bchBalance = "Balance: " + balance.toString() + " BCH";
                    balanceTxtView.setText(bchBalance);
                    symbolLabel.setText("₿");
                    showingFiat = false;
                    updateAlternateAmount();
                } else {
                    try {
                        String balanceStr = "Balance: " + ExchangeRates.getInstance().getFormattedAmountInFiat(balance, Currency.getInstance(fiatCurrency));
                        balanceTxtView.setText(balanceStr);
                        symbolLabel.setText(Currency.getInstance(fiatCurrency).getSymbol());
                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                    showingFiat = true;
                    updateAlternateAmount();
                }
            }
        });

        TextInputEditText address = (TextInputEditText) findViewById(R.id.addressInput);
        address.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                final int DRAWABLE_RIGHT = 2;

                if(event.getAction() == MotionEvent.ACTION_UP) {
                    if(event.getX() >= (address.getRight() - address.getCompoundDrawables()[DRAWABLE_RIGHT].getBounds().width())) {
                        System.out.println("here");
                        return true;
                    }
                }
                return false;
            }
        });

        TextView feeText = (TextView) findViewById(R.id.feeText);
        String feeStr = "Network Fee: " + satPerByte + " sat/byte";
        feeText.setText(feeStr);

        address.addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {}

            @Override
            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    boolean valid = wallet.validateAddress(address.getText().toString());
                    if (valid) {
                        TextInputLayout amountLayout = (TextInputLayout) findViewById(R.id.amountLayout);
                        amountLayout.setError(null);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        Button send = (Button) findViewById(R.id.sendBtn);
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TextInputLayout layout = (TextInputLayout) findViewById(R.id.addressLayout);
                try {
                    boolean inputError = false;
                    boolean valid = wallet.validateAddress(address.getText().toString());
                    if (!valid) {
                        layout.setError("INVALID BITCOIN CASH ADDRESS");
                        inputError = true;
                    } else {
                        layout.setError(null);
                    }
                    Amount toSpend;
                    if (inputAmount.getText().toString().equals("")) {
                        TextInputEditText etAmount = (TextInputEditText) findViewById(R.id.amountInput);
                        etAmount.setError("INVALID AMOUNT");
                        inputError = true;
                    }
                    if (inputError) {
                        return;
                    }
                    if (showingFiat) {
                        double fiatAmount = Double.valueOf(inputAmount.getText().toString());
                        toSpend = new Amount(ExchangeRates.getInstance().convertToBCH(fiatAmount, Currency.getInstance(fiatCurrency)));
                    } else {
                        double bchAmount = Double.valueOf(inputAmount.getText().toString());
                        toSpend = new Amount(bchAmount);
                    }
                    if (toSpend.getSatoshis() == 0 || toSpend.getSatoshis() > balance.getSatoshis()) {
                        TextInputEditText etAmount = (TextInputEditText) findViewById(R.id.amountInput);
                        etAmount.setError("INVALID AMOUNT");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        ToggleButton sendAllBtn = (ToggleButton) findViewById(R.id.sendAllBtn);
        sendAllBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    if (showingFiat) {
                        try {
                            String formatted = ExchangeRates.getInstance().getFormattedAmountInFiat(balance, Currency.getInstance(fiatCurrency));
                            inputAmount.setText(formatted.substring(1));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else {
                        inputAmount.setText(balance.toString());
                    }
                    updateAlternateAmount();
                    sendAll = true;
                } else {
                    inputAmount.setText("0");
                    updateAlternateAmount();
                    sendAll = false;
                }
            }
        });
    }

    public void updateAlternateAmount() {
        if (showingFiat) {
            if (inputAmount.getText().toString().equals("") || inputAmount.getText().toString().equals(".")) {
                String zeroBCH = "0 BCH";
                conversionRate.setText(zeroBCH);
                return;
            }
            double fiatAmount = Double.valueOf(inputAmount.getText().toString());
            try {
                Amount bchRate = new Amount(ExchangeRates.getInstance().convertToBCH(fiatAmount, Currency.getInstance(fiatCurrency)));
                String bchRateStr = bchRate.toString() + " BCH";
                conversionRate.setText(bchRateStr);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            if (inputAmount.getText().toString().equals("") || inputAmount.getText().toString().equals(".")) {
                String zeroFiat = Currency.getInstance(fiatCurrency).getSymbol() + "0";
                conversionRate.setText(zeroFiat);
                return;
            }
            double bchAmount = Double.valueOf(inputAmount.getText().toString());
            try {
                Amount bchRate = new Amount(bchAmount);
                String formatted = ExchangeRates.getInstance().getFormattedAmountInFiat(bchRate, Currency.getInstance(fiatCurrency));
                conversionRate.setText(formatted);
            } catch (Exception e) {
                e.printStackTrace();
            }
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