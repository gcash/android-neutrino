package cash.bchd.android_neutrino;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TextInputEditText;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.nio.charset.Charset;
import java.util.Currency;

import androidmads.library.qrgenearator.QRGContents;
import androidmads.library.qrgenearator.QRGEncoder;
import cash.bchd.android_neutrino.wallet.AddressListener;
import cash.bchd.android_neutrino.wallet.Amount;
import cash.bchd.android_neutrino.wallet.BitcoinPaymentURI;
import cash.bchd.android_neutrino.wallet.ExchangeRates;
import cash.bchd.android_neutrino.wallet.Wallet;

public class ReceiveActivity extends AppCompatActivity {

    boolean showingFiat = true;
    String fiatCurrency;
    String lastAddress;
    TextInputEditText receiveAmountInput;
    TextView conversionRate;
    Wallet wallet;
    LinearLayout receiveLayout;
    TextInputEditText label;
    TextInputEditText memo;
    long totalReceived;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_request);

        CloseActivity.cancelCloseTimer();

        Toolbar myToolbar = findViewById(R.id.receiveToolbar);
        setSupportActionBar(myToolbar);
        ActionBar supportActionBar = getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayShowTitleEnabled(false);
            supportActionBar.setDisplayHomeAsUpEnabled(true);
            supportActionBar.setDisplayShowHomeEnabled(true);
        }

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            fiatCurrency = extras.getString("fiatCurrency");
            lastAddress = extras.getString("lastAddress");
        }
        receiveAmountInput = findViewById(R.id.receiveAmountInput);
        conversionRate = findViewById(R.id.receiveConversionRate);

        wallet = Wallet.getInstance();

        TextView symbolLabel = findViewById(R.id.receiveSymbolLabel);
        receiveLayout = findViewById(R.id.receiveLayout);
        label = findViewById(R.id.labelInput);
        memo = findViewById(R.id.receiveMemoInput);

        if (extras != null) {
            String defaultMemo = extras.getString("defaultMemo");
            String defaultLabel = extras.getString("defaultLabel");
            if (defaultLabel != null && !defaultLabel.equals("")) {
                label.setText(defaultLabel);
            }
            if (defaultMemo != null && !defaultMemo.equals("")) {
                memo.setText(defaultMemo);
            }
        }

        symbolLabel.setText(Currency.getInstance(fiatCurrency).getSymbol());

        ImageView toggle = findViewById(R.id.receiveToggleImage);
        toggle.setOnClickListener(v -> toggleBchFiat());

        receiveAmountInput.addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {}

            @Override
            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateAlternateAmount();
            }
        });

        Button requestButton = findViewById(R.id.requestBtn);
        requestButton.setOnClickListener(v -> {
            totalReceived = 0;
            displayQRPopup();
        });
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void toggleBchFiat() {
        if (receiveAmountInput.getText() == null) {
            return;
        }
        TextView symbolLabel = findViewById(R.id.receiveSymbolLabel);
        if (showingFiat) {
            String amt = receiveAmountInput.getText().toString();
            symbolLabel.setText("₿");
            if (!amt.equals("")) {
                try {
                    Amount bchRate = new Amount(ExchangeRates.getInstance().convertToBCH(Double.valueOf(amt), Currency.getInstance(fiatCurrency)));
                    receiveAmountInput.setText(bchRate.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            showingFiat = false;
            updateAlternateAmount();
        } else {
            try {
                symbolLabel.setText(Currency.getInstance(fiatCurrency).getSymbol());
                String amt = receiveAmountInput.getText().toString();
                if (!amt.equals("")) {
                    try {
                        Amount bchRate = new Amount(Double.valueOf(amt));
                        String formatted = ExchangeRates.getInstance().getFormattedAmountInFiat(bchRate, Currency.getInstance(fiatCurrency));
                        receiveAmountInput.setText(formatted.substring(1));
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
        Editable receiveAmountInputText = receiveAmountInput.getText();
        if (receiveAmountInputText == null) {
            return;
        }
        if (showingFiat) {
            if (SendActivity.considerAmountTextZero(receiveAmountInputText)) {
                String zeroBCH = "0 BCH";
                conversionRate.setText(zeroBCH);
                return;
            }
            double fiatAmount = Double.valueOf(receiveAmountInputText.toString().replace(",", "."));
            try {
                Amount bchRate = new Amount(ExchangeRates.getInstance().convertToBCH(fiatAmount, Currency.getInstance(fiatCurrency)));
                conversionRate.setText(getString(R.string.bch_amount, bchRate.toString()));
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            if (SendActivity.considerAmountTextZero(receiveAmountInputText)) {
                String zeroFiat = Currency.getInstance(fiatCurrency).getSymbol() + "0";
                conversionRate.setText(zeroFiat);
                return;
            }
            double bchAmount = Double.valueOf(receiveAmountInputText.toString().replace(",", "."));
            try {
                Amount bchRate = new Amount(bchAmount);
                String formatted = ExchangeRates.getInstance().getFormattedAmountInFiat(bchRate, Currency.getInstance(fiatCurrency));
                conversionRate.setText(formatted);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void displayQRPopup() {
        String lastAddr = "";
        if (!wallet.isRunning()) {
            lastAddr = lastAddress;
            if (lastAddr.equals("")) {
                Snackbar snackbar = Snackbar.make(receiveLayout, R.string.wallet_is_not_loaded_yet, Snackbar.LENGTH_LONG);
                snackbar.show();
                return;
            }
        }
        LayoutInflater layoutInflater = (LayoutInflater) ReceiveActivity.this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View customView = layoutInflater.inflate(R.layout.requestpopup,null);
        PopupWindow popupWindow = new PopupWindow(customView, CoordinatorLayout.LayoutParams.WRAP_CONTENT, CoordinatorLayout.LayoutParams.WRAP_CONTENT, true);
        popupWindow.showAtLocation(findViewById(R.id.receiveLayout), Gravity.CENTER, 0, 0);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        try {
            String addr;
            if (wallet.isRunning()) {
                addr = wallet.currentAddress();
            } else {
                addr = lastAddr;
            }
            WindowManager manager = (WindowManager) getSystemService(WINDOW_SERVICE);
            Display display = manager.getDefaultDisplay();
            Point point = new Point();
            display.getSize(point);
            int width = point.x;
            int height = point.y;
            int smallerDimension = width < height ? width : height;
            final int finalDimension = smallerDimension;

            BitcoinPaymentURI.Builder builder = new BitcoinPaymentURI.Builder();
            builder.address(addr);

            Amount requestAmount = null;
            if (receiveAmountInput.getText() != null && !receiveAmountInput.getText().toString().equals("")) {
                if (showingFiat) {
                    double bchAmount = ExchangeRates.getInstance().convertToBCH(Double.valueOf(receiveAmountInput.getText().toString().replace(",", ".")), Currency.getInstance(fiatCurrency));
                    requestAmount = new Amount(bchAmount);
                    builder.amount(bchAmount);
                } else {
                    double bchAmount = Double.valueOf(receiveAmountInput.getText().toString().replace(",", "."));
                    requestAmount = new Amount(bchAmount);
                    builder.amount(bchAmount);
                }
            }

            final Amount compareAmount = requestAmount;

            if (label.getText() != null && !label.getText().toString().equals("")) {
                builder.label(label.getText().toString());
            }

            if (memo.getText() != null && !memo.getText().toString().equals("")) {
                builder.message(memo.getText().toString());
            }

            BitcoinPaymentURI uri = builder.build();

            QRGEncoder qrgEncoder = new QRGEncoder(
                    uri.getURI(), null,
                    QRGContents.Type.TEXT,
                    smallerDimension);
            Bitmap bitmap = qrgEncoder.encodeAsBitmap();
            ImageView qrImage = customView.findViewById(R.id.requestQRCode);

            qrImage.setImageBitmap(bitmap);

            View.OnClickListener onClickListener = v -> copyToClipboard(uri.getURI());

            qrImage.setOnClickListener(onClickListener);

            TextView addrText = customView.findViewById(R.id.requestURi);
            addrText.setText(uri.getURI());
            addrText.setOnClickListener(onClickListener);

            TextView helperText = customView.findViewById(R.id.uriHelpText);
            helperText.setText(getString(R.string.send_bch_to, requestAmount.toString()));

            NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this);
            if (nfcAdapter != null) {
                NdefRecord uriRecord = new NdefRecord(
                        NdefRecord.TNF_ABSOLUTE_URI,
                        uri.getURI().getBytes(Charset.forName("US-ASCII")),
                        new byte[0], new byte[0]);
                nfcAdapter.setNdefPushMessage(new NdefMessage(uriRecord), this);
            }

            wallet.listenAddress(addr, new AddressListener() {
                @Override
                public void onPaymentReceived(long amount) {
                    if (getApplicationContext() != null) {
                        runOnUiThread(() -> {
                            totalReceived += amount;
                            if (totalReceived >= compareAmount.getSatoshis()) {
                                Vibrator vibrator = (Vibrator) getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
                                vibrator.vibrate(500);
                                LinearLayout qrLayout = customView.findViewById(R.id.requestQRLayout);
                                int h = qrLayout.getHeight();
                                qrLayout.setHorizontalGravity(Gravity.CENTER_HORIZONTAL);
                                qrLayout.setVerticalGravity(Gravity.CENTER_VERTICAL);

                                qrImage.setVisibility(View.GONE);
                                addrText.setVisibility(View.GONE);
                                helperText.setVisibility(View.GONE);
                                TextView partialPayment = customView.findViewById(R.id.parialPayment);
                                partialPayment.setVisibility(View.GONE);
                                final GifView showGifView = new GifView(getApplicationContext());

                                showGifView.setGifImageDrawableId(R.drawable.coinflip);
                                showGifView.drawGif();
                                showGifView.setForegroundGravity(Gravity.CENTER);


                                ViewGroup.LayoutParams params = qrLayout.getLayoutParams();
                                Double dh = (double) h;
                                Double truncatedH = dh * 0.8;
                                System.out.println(truncatedH);
                                System.out.println(h);
                                params.height = 1092;
                                params.width = 1366;

                                qrLayout.requestLayout();
                                qrLayout.addView(showGifView);
                            } else {
                                TextView partialPayment = customView.findViewById(R.id.parialPayment);
                                partialPayment.setVisibility(View.VISIBLE);

                                TextView helperText1 = customView.findViewById(R.id.uriHelpText);
                                Amount newAmt = new Amount(compareAmount.getSatoshis() - totalReceived);
                                helperText1.setText(getString(R.string.send_bch_to, newAmt.toString()));

                                builder.amount(newAmt.toBCH());

                                BitcoinPaymentURI newUri = builder.build();

                                QRGEncoder qrgEncoder1 = new QRGEncoder(
                                        newUri.getURI(), null,
                                        QRGContents.Type.TEXT,
                                        finalDimension);

                                Bitmap newBitmap = null;
                                try {
                                    newBitmap = qrgEncoder1.encodeAsBitmap();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                ImageView qrImage1 = customView.findViewById(R.id.requestQRCode);

                                qrImage1.setImageBitmap(newBitmap);

                                qrImage1.setOnClickListener(v -> copyToClipboard(newUri.getURI()));

                                TextView addrText1 = customView.findViewById(R.id.requestURi);
                                addrText1.setText(newUri.getURI());
                                addrText1.setOnClickListener(v -> copyToClipboard(newUri.getURI()));

                                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) helperText1.getLayoutParams();
                                params.setMargins(params.leftMargin, -25, params.rightMargin, params.bottomMargin);
                            }
                        });
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void copyToClipboard(String data) {
        Object clipboardService = getSystemService(CLIPBOARD_SERVICE);
        final ClipboardManager clipboardManager = (ClipboardManager)clipboardService;
        if (clipboardManager != null) {
            ClipData clipData = ClipData.newPlainText(getString(R.string.source_text), data);
            clipboardManager.setPrimaryClip(clipData);
            Snackbar snackbar = Snackbar.make(receiveLayout, R.string.uri_copied_to_clipboard, Snackbar.LENGTH_LONG);
            snackbar.show();
        }
    }
}
