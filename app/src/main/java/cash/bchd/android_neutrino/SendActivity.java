package cash.bchd.android_neutrino;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

import cash.bchd.android_neutrino.wallet.Amount;
import cash.bchd.android_neutrino.wallet.BitcoinPaymentURI;
import cash.bchd.android_neutrino.wallet.ExchangeRates;
import cash.bchd.android_neutrino.wallet.Network;
import cash.bchd.android_neutrino.wallet.Wallet;
import walletrpc.Api;

public class SendActivity extends AppCompatActivity {

    public static final int RC_BARCODE_CAPTURE = 9001;

    Wallet wallet;
    Network network;
    TextView balanceTxtView;
    Amount balance;
    String fiatCurrency;
    boolean showingFiat = true;
    TextInputEditText inputAmount;
    TextView conversionRate;
    boolean sendAll;
    LinearLayout sendLayout;
    TextInputEditText address;
    TextInputEditText memo;
    TextView symbolLabel;
    String label;
    int satPerByte;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send);
        SystemBars.apply(this);

        CloseActivity.cancelCloseTimer();

        sendLayout = findViewById(R.id.sendLayout);

        Intent intent = getIntent();

        fiatCurrency = intent.getExtras().getString("fiatCurrency");
        satPerByte = intent.getExtras().getInt("feePerByte");

        wallet = Wallet.getInstance();
        network = Settings.get(this).getNetwork();
        Networks.showBadge(this, network);

        Toolbar myToolbar = findViewById(R.id.sendToolbar);
        setSupportActionBar(myToolbar);
        ActionBar supportActionBar = getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayShowTitleEnabled(false);
            supportActionBar.setDisplayHomeAsUpEnabled(true);
            supportActionBar.setDisplayShowHomeEnabled(true);
        }

        balanceTxtView = findViewById(R.id.sendBalance);
        try {
            balance = new Amount(wallet.balance());
            if (network.hasFiat) {
                balanceTxtView.setText(getString(R.string.balance_fiat_amount, ExchangeRates.getInstance().getFormattedAmountInFiat(balance, Currency.getInstance(fiatCurrency))));
            } else {
                balanceTxtView.setText(getString(R.string.balance_bch_amount, balance.toString()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        symbolLabel = findViewById(R.id.symbolLabel);

        symbolLabel.setText(Currency.getInstance(fiatCurrency).getSymbol());

        inputAmount = findViewById(R.id.amountInput);
        conversionRate = findViewById(R.id.conversionRate);
        inputAmount.addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateAlternateAmount();
                if (inputAmount.getText() != null && !inputAmount.getText().toString().equals("")) {
                    TextInputEditText etAmount = findViewById(R.id.amountInput);
                    etAmount.setError(null);
                }
            }
        });

        ImageView toggle = findViewById(R.id.toggleImage);
        toggle.setOnClickListener(v -> toggleBchFiat());
        if (!network.hasFiat) {
            // Test coins have no price, so amounts are entered in BCH only.
            showingFiat = false;
            symbolLabel.setText("₿");
            toggle.setVisibility(View.GONE);
            conversionRate.setVisibility(View.GONE);
        }

        address = findViewById(R.id.addressInput);
        TextInputLayout addressLayout = findViewById(R.id.addressLayout);
        addressLayout.setEndIconOnClickListener(v -> {
            Intent intent12 = new Intent(getApplicationContext(), ScannerActivity.class);
            startActivityForResult(intent12, RC_BARCODE_CAPTURE);
        });

        memo = findViewById(R.id.memoInput);

        TextView feeText = findViewById(R.id.feeText);
        String feeStr = getResources().getQuantityString(R.plurals.network_fee_sat_per_byte, satPerByte, satPerByte);
        feeText.setText(feeStr);

        address.addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    boolean valid = address.getText() != null && wallet.validateAddress(address.getText().toString());
                    if (valid) {
                        TextInputLayout amountLayout = findViewById(R.id.amountLayout);
                        amountLayout.setError(null);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        Button send = findViewById(R.id.sendBtn);
        send.setOnClickListener(v -> {
            TextInputLayout layout = findViewById(R.id.addressLayout);
            try {
                boolean inputError = false;
                boolean valid = address.getText() != null && wallet.validateAddress(address.getText().toString());
                if (!valid) {
                    layout.setError(getString(R.string.invalid_bitcoin_cash_address));
                    inputError = true;
                } else {
                    layout.setError(null);
                }
                Amount toSpend = null;
                if (inputAmount.getText() != null && inputAmount.getText().toString().equals("")) {
                    TextInputEditText etAmount = findViewById(R.id.amountInput);
                    etAmount.setError(getString(R.string.invalid_amount));
                    inputError = true;
                }
                if (inputError) {
                    return;
                }

                if (inputAmount.getText() != null) {
                    if (showingFiat) {
                        double fiatAmount = Double.valueOf(inputAmount.getText().toString().replace(",", "."));
                        toSpend = new Amount(ExchangeRates.getInstance().convertToBCH(fiatAmount, Currency.getInstance(fiatCurrency)));
                    } else {
                        toSpend = Amount.fromBCH(inputAmount.getText().toString());
                    }
                }

                if (toSpend != null && (toSpend.getSatoshis() == 0 || toSpend.getSatoshis() > balance.getSatoshis())) {
                    TextInputEditText etAmount = findViewById(R.id.amountInput);
                    etAmount.setError(getString(R.string.invalid_amount));
                    return;
                }
                byte[] serializedTx = null;
                long txFee = 0;
                List<Long> inputVals = null;
                if (sendAll) {
                    Api.SweepAccountResponse tx = wallet.sweepAccount(address.getText().toString(), satPerByte);
                    serializedTx = tx.getSerializedTransaction().toByteArray();
                    txFee = tx.getFee();
                    inputVals = tx.getInputValuesList();
                } else if (toSpend != null) {
                    Api.CreateTransactionResponse tx = wallet.createTransaction(address.getText().toString(), toSpend.getSatoshis(), satPerByte);
                    serializedTx = tx.getSerializedTransaction().toByteArray();
                    txFee = tx.getFee();
                    inputVals = tx.getInputValuesList();
                }

                String fiatFormatted = network.hasFiat ? ExchangeRates.getInstance().getFormattedAmountInFiat(toSpend, Currency.getInstance(fiatCurrency)) : "";
                ArrayList<String> inputStrings = new ArrayList<>();

                if (inputVals != null && !inputVals.isEmpty()) {
                    for (Long val : inputVals) {
                        inputStrings.add(String.valueOf(val));
                    }
                }

                Intent intent1 = new Intent(getApplicationContext(), ConfirmationActivity.class);
                intent1.putExtra("paymentAddress", address.getText().toString());
                intent1.putExtra("amountBCH", toSpend.toString());
                intent1.putExtra("amountFiat", fiatFormatted);
                intent1.putExtra("fee", txFee);
                intent1.putExtra("serializedTransaction", serializedTx);
                intent1.putStringArrayListExtra("inputVals", inputStrings);

                if (memo.getText() != null && !memo.getText().toString().equals("")) {
                    intent1.putExtra("memo", memo.getText().toString());
                }
                if (label != null && !label.equals("")) {
                    intent1.putExtra("label", label);
                }
                startActivityForResult(intent1, ConfirmationActivity.RC_PAY);
            } catch (Exception e) {
                // Say why bchwallet refused the transaction, such as a dust amount, when it did.
                String reason = Wallet.reasonFor(e);
                String message = reason != null
                        ? getString(R.string.error_creating_transaction_reason, reason)
                        : getString(R.string.error_creating_transaction);
                Snackbar snackbar = Snackbar.make(sendLayout, message, Snackbar.LENGTH_LONG);
                snackbar.setTextMaxLines(5);
                snackbar.show();
                e.printStackTrace();
            }
        });

        ToggleButton sendAllBtn = findViewById(R.id.sendAllBtn);
        sendAllBtn.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (showingFiat) {
                    toggleBchFiat();
                }
                inputAmount.setText(balance.toString());
                inputAmount.setEnabled(false);
                updateAlternateAmount();
                sendAll = true;
            } else {
                inputAmount.setText("");
                updateAlternateAmount();
                sendAll = false;
                inputAmount.setEnabled(true);
            }
        });

        String qrData = intent.getExtras().getString("qrdata");
        if (qrData != null) {
            this.onActivityResult(RC_BARCODE_CAPTURE, RESULT_OK, intent);
            return;
        }

        byte[] paymentRequest = intent.getByteArrayExtra(PaymentRequestFile.EXTRA);
        if (paymentRequest != null) {
            PaymentRequests.payFile(this, sendLayout, wallet, paymentRequest, fiatCurrency, satPerByte);
            return;
        }

        Uri data = getIntent().getData();
        if (data != null) {
            Intent newIntent = new Intent();
            newIntent.putExtra("qrdata", data.toString());
            this.onActivityResult(RC_BARCODE_CAPTURE, RESULT_OK, newIntent);
            return;
        }
    }

    public void toggleBchFiat() {
        if (showingFiat) {
            balanceTxtView.setText(getString(R.string.balance_bch_amount, balance.toString()));
            symbolLabel.setText("₿");

            String amt = inputAmount.getText().toString().replace(",", ".");
            if (!amt.equals("")) {
                try {
                    Amount bchRate = new Amount(ExchangeRates.getInstance().convertToBCH(Double.valueOf(amt), Currency.getInstance(fiatCurrency)));
                    inputAmount.setText(bchRate.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            showingFiat = false;
            updateAlternateAmount();
        } else {
            try {
                balanceTxtView.setText(getString(R.string.balance_fiat_amount, ExchangeRates.getInstance().getFormattedAmountInFiat(balance, Currency.getInstance(fiatCurrency))));
                symbolLabel.setText(Currency.getInstance(fiatCurrency).getSymbol());

                if (inputAmount.getText() != null) {
                    String amt = inputAmount.getText().toString().replace(",", ".");
                    if (!amt.equals("")) {
                        try {
                            Amount bchRate = new Amount(Double.valueOf(amt));
                            inputAmount.setText(ExchangeRates.getInstance().getAmountInFiat(bchRate, Currency.getInstance(fiatCurrency)));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            showingFiat = true;
            updateAlternateAmount();
        }
    }

    public void updateAlternateAmount() {
        Editable inputAmountText = inputAmount.getText();
        if (inputAmountText == null || !network.hasFiat) {
            return;
        }
        if (showingFiat) {
            if (considerAmountTextZero(inputAmountText)) {
                conversionRate.setText(getString(R.string.bch_amount, "0"));
                return;
            }
            double fiatAmount = Double.valueOf(inputAmountText.toString().replace(",", "."));
            try {
                Amount bchRate = new Amount(ExchangeRates.getInstance().convertToBCH(fiatAmount, Currency.getInstance(fiatCurrency)));
                conversionRate.setText(getString(R.string.bch_amount, bchRate.toString()));
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            if (considerAmountTextZero(inputAmountText)) {
                String zeroFiat = Currency.getInstance(fiatCurrency).getSymbol() + "0";
                conversionRate.setText(zeroFiat);
                return;
            }
            double bchAmount = Double.valueOf(inputAmountText.toString().replace(",", "."));
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
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_BARCODE_CAPTURE) {
            if (resultCode == RESULT_OK) {
                if (data != null) {
                    String qrdata = data.getStringExtra("qrdata");
                    Network linkNetwork = Network.ofUri(qrdata);
                    if (linkNetwork != null && linkNetwork != network) {
                        Snackbar snackbar = Snackbar.make(sendLayout, getString(R.string.payment_link_other_network,
                                Networks.name(this, linkNetwork), Networks.name(this, network)), Snackbar.LENGTH_LONG);
                        snackbar.setTextMaxLines(5);
                        snackbar.show();
                        return;
                    }

                    BitcoinPaymentURI uri = BitcoinPaymentURI.parse(qrdata);
                    if (uri != null) {
                        if (uri.getR() != null) {
                            processPaymentRequest(qrdata);
                            return;
                        }

                        address.setText(uri.getAddress());
                        if (uri.getAmount() != null) {
                            if (showingFiat) {
                                toggleBchFiat();
                            }
                            Amount uriAmt = new Amount(uri.getAmount());
                            inputAmount.setText(uriAmt.toString());
                        }
                        if (uri.getMessage() != null) {
                            memo.setText(uri.getMessage());
                        }
                        if (uri.getLabel() != null) {
                            label = uri.getLabel();
                        }
                    } else {
                        address.setText(qrdata);
                    }
                    try {
                        boolean valid = address.getText() != null && wallet.validateAddress(address.getText().toString());
                        TextInputLayout layout = findViewById(R.id.addressLayout);
                        if (!valid) {
                            layout.setError(getString(R.string.invalid_bitcoin_cash_address));
                        } else {
                            layout.setError(null);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } else if (resultCode != RESULT_CANCELED) {
                Snackbar snackbar = Snackbar.make(sendLayout, R.string.barcode_read_error, Snackbar.LENGTH_LONG);
                snackbar.show();
            }
        } else if (requestCode == ConfirmationActivity.RC_PAY) {
            // The payment was sent, and the confirmation screen closed.
            if (resultCode == RESULT_OK) {
                finish();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
            Uri uri = intent.getData();
            String uriString = intent.getDataString();

            Intent newIntent = new Intent();
            newIntent.putExtra("qrdata", uriString);
            this.onActivityResult(RC_BARCODE_CAPTURE, RESULT_OK, newIntent);
        }
    }

    private void processPaymentRequest(String qrdata) {
        PaymentRequests.pay(this, sendLayout, wallet, qrdata, fiatCurrency, satPerByte);
    }

    static boolean considerAmountTextZero(Editable inputAmountText) {
        return inputAmountText.toString().equals("") || inputAmountText.toString().equals(".") || inputAmountText.toString().equals(",");
    }
}