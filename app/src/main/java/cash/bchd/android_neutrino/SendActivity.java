package cash.bchd.android_neutrino;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.support.design.widget.Snackbar;
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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.vision.barcode.Barcode;

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

import cash.bchd.android_neutrino.wallet.Amount;
import cash.bchd.android_neutrino.wallet.BitcoinPaymentURI;
import cash.bchd.android_neutrino.wallet.ExchangeRates;
import cash.bchd.android_neutrino.wallet.Wallet;
import walletrpc.Api;

public class SendActivity extends AppCompatActivity {

    public static final int RC_BARCODE_CAPTURE = 9001;

    Wallet wallet;
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
    public static Activity fa;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send);
        fa = this;

        CloseActivity.cancelCloseTimer();

        Intent intent = getIntent();
        fiatCurrency = intent.getExtras().getString("fiatCurrency");
        satPerByte = intent.getExtras().getInt("feePerByte");

        wallet = Wallet.getInstance();


        Toolbar myToolbar = (Toolbar) findViewById(R.id.sendToolbar);
        setSupportActionBar(myToolbar);

        getSupportActionBar().setDisplayShowTitleEnabled(false);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        sendLayout = (LinearLayout) findViewById(R.id.sendLayout);

        balanceTxtView = (TextView) findViewById(R.id.sendBalance);
        try {
            balance = new Amount(wallet.balance());
            String balanceStr = "Balance: " + ExchangeRates.getInstance().getFormattedAmountInFiat(balance, Currency.getInstance(fiatCurrency));
            balanceTxtView.setText(balanceStr);

        } catch(Exception e) {
            e.printStackTrace();
        }

        symbolLabel = (TextView) findViewById(R.id.symbolLabel);

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
                toggleBchFiat();
            }
        });

        address = (TextInputEditText) findViewById(R.id.addressInput);
        address.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                final int DRAWABLE_RIGHT = 2;

                if(event.getAction() == MotionEvent.ACTION_UP) {
                    if(event.getX() >= (address.getRight() - address.getCompoundDrawables()[DRAWABLE_RIGHT].getBounds().width())) {
                        Intent intent = new Intent(getApplicationContext(), ScannerActivity.class);
                        startActivityForResult(intent, RC_BARCODE_CAPTURE);
                        return true;
                    }
                }
                return false;
            }
        });

        memo = (TextInputEditText) findViewById(R.id.memoInput);

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

                    } else {
                        Api.CreateTransactionResponse tx = wallet.createTransaction(address.getText().toString(), toSpend.getSatoshis(), satPerByte);
                        serializedTx = tx.getSerializedTransaction().toByteArray();
                        txFee = tx.getFee();
                        inputVals = tx.getInputValuesList();
                    }

                    String fiatFormatted = ExchangeRates.getInstance().getFormattedAmountInFiat(toSpend, Currency.getInstance(fiatCurrency));
                    ArrayList<String> inputStrings = new ArrayList<String>();
                    for (Long val : inputVals) {
                        inputStrings.add(String.valueOf(val));
                    }

                    Intent intent = new Intent(getApplicationContext(), ConfirmationActivity.class);
                    intent.putExtra("paymentAddress", address.getText().toString());
                    intent.putExtra("amountBCH", toSpend.toString());
                    intent.putExtra("amountFiat", fiatFormatted);
                    intent.putExtra("fee", txFee);
                    intent.putExtra("serializedTransaction", serializedTx);
                    intent.putStringArrayListExtra("inputVals", inputStrings);

                    if (memo.getText() != null && !memo.getText().toString().equals("")) {
                        intent.putExtra("memo", memo.getText().toString());
                    }
                    if (label != null && !label.equals("")) {
                        intent.putExtra("label", label);
                    }
                    startActivity(intent);
                } catch (Exception e) {
                    Snackbar snackbar = Snackbar.make(sendLayout, "Error creating transaction.", Snackbar.LENGTH_LONG);
                    snackbar.show();
                    e.printStackTrace();
                }
            }
        });

        ToggleButton sendAllBtn = (ToggleButton) findViewById(R.id.sendAllBtn);
        sendAllBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
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
            }
        });

        String qrData = intent.getExtras().getString("qrdata");
        if (qrData != null) {
            this.onActivityResult(RC_BARCODE_CAPTURE, CommonStatusCodes.SUCCESS, intent);
            return;
        }

        Uri data = getIntent().getData();
        if (data != null) {
            Intent newIntent = new Intent();
            newIntent.putExtra("qrdata", data.toString());
            this.onActivityResult(RC_BARCODE_CAPTURE, CommonStatusCodes.SUCCESS, newIntent);
        }
    }

    public void toggleBchFiat() {
        if (showingFiat) {
            String bchBalance = "Balance: " + balance.toString() + " BCH";
            balanceTxtView.setText(bchBalance);
            symbolLabel.setText("₿");


            String amt = inputAmount.getText().toString();
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
                String balanceStr = "Balance: " + ExchangeRates.getInstance().getFormattedAmountInFiat(balance, Currency.getInstance(fiatCurrency));
                balanceTxtView.setText(balanceStr);
                symbolLabel.setText(Currency.getInstance(fiatCurrency).getSymbol());

                String amt = inputAmount.getText().toString();
                if (!amt.equals("")) {
                    try {
                        Amount bchRate = new Amount(Double.valueOf(amt));
                        String formatted = ExchangeRates.getInstance().getFormattedAmountInFiat(bchRate, Currency.getInstance(fiatCurrency));
                        inputAmount.setText(formatted.substring(1));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

            } catch(Exception e) {
                e.printStackTrace();
            }
            showingFiat = true;
            updateAlternateAmount();
        }
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
                finish();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_BARCODE_CAPTURE) {
            if (resultCode == CommonStatusCodes.SUCCESS) {
                if (data != null) {
                    String qrdata = data.getStringExtra("qrdata");

                    BitcoinPaymentURI uri = BitcoinPaymentURI.parse(qrdata);
                    if (uri != null) {
                        if (uri.getR() != null){
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
                        boolean valid = wallet.validateAddress(address.getText().toString());
                        TextInputLayout layout = (TextInputLayout) findViewById(R.id.addressLayout);
                        if (!valid) {
                            layout.setError("INVALID BITCOIN CASH ADDRESS");
                        } else {
                            layout.setError(null);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return;
                }
            } else if (resultCode != 0) {
                Snackbar snackbar = Snackbar.make(sendLayout, "Barcode Read Error", Snackbar.LENGTH_LONG);
                snackbar.show();
            }
        }
        else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void processPaymentRequest(String qrdata) {
        try {
            Api.DownloadPaymentRequestResponse pr = wallet.downloadPaymentRequest(qrdata);
            List<Api.CreateTransactionRequest.Output> outputs = new ArrayList<Api.CreateTransactionRequest.Output>();
            long totalSatoshis = 0;
            for (Api.DownloadPaymentRequestResponse.Output out : pr.getOutputsList()) {
                Api.CreateTransactionRequest.Output output = Api.CreateTransactionRequest.Output.newBuilder().setAmount(out.getAmount()).setAddress(out.getAddress()).build();
                outputs.add(output);
                totalSatoshis += out.getAmount();
            }
            Amount totalAmt = new Amount(totalSatoshis);
            String fiatFormatted = ExchangeRates.getInstance().getFormattedAmountInFiat(totalAmt, Currency.getInstance(fiatCurrency));

            if (totalSatoshis > wallet.balance()) {
                System.out.println(totalSatoshis);
                System.out.println(wallet.balance());
                Snackbar snackbar = Snackbar.make(sendLayout, "Insufficient Funds", Snackbar.LENGTH_LONG);
                snackbar.show();
                return;
            }

            Api.CreateTransactionResponse tx = wallet.createTransaction(outputs, satPerByte);
            byte[] serializedTx = tx.getSerializedTransaction().toByteArray();
            long txFee = tx.getFee();
            List<Long> inputVals = tx.getInputValuesList();

            ArrayList<String> inputStrings = new ArrayList<String>();
            for (Long val : inputVals) {
                inputStrings.add(String.valueOf(val));
            }

            Intent intent = new Intent(getApplicationContext(), ConfirmationActivity.class);
            intent.putExtra("paymentAddress", outputs.get(0).getAddress());
            intent.putExtra("amountBCH", totalAmt.toString());
            intent.putExtra("amountFiat", fiatFormatted);
            intent.putExtra("fee", txFee);
            intent.putExtra("serializedTransaction", serializedTx);
            intent.putStringArrayListExtra("inputVals", inputStrings);
            intent.putExtra("memo", pr.getMemo());
            intent.putExtra("label", pr.getPayToName());

            intent.putExtra("isPaymentRequest", true);
            intent.putExtra("merchantData", pr.getMerchantData().toByteArray());
            intent.putExtra("paymentURL", pr.getPaymentUrl());
            intent.putExtra("refundAddress", wallet.currentAddress());
            intent.putExtra("refundAmount", totalSatoshis);
            startActivity(intent);

        } catch (Exception e) {
            Snackbar snackbar = Snackbar.make(sendLayout, "Invalid Payment Request", Snackbar.LENGTH_LONG);
            snackbar.show();
            e.printStackTrace();
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
            this.onActivityResult(RC_BARCODE_CAPTURE, CommonStatusCodes.SUCCESS, newIntent);
        }
    }
}