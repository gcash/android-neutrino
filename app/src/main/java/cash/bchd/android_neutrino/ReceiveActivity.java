package cash.bchd.android_neutrino;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Vibrator;
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Currency;

import cash.bchd.android_neutrino.wallet.AddressListener;
import cash.bchd.android_neutrino.wallet.Amount;
import cash.bchd.android_neutrino.wallet.BitcoinPaymentURI;
import cash.bchd.android_neutrino.wallet.ExchangeRates;
import cash.bchd.android_neutrino.wallet.Network;
import cash.bchd.android_neutrino.wallet.Wallet;

public class ReceiveActivity extends AppCompatActivity {

    // A recreated screen needs the unit the amount is in besides the fields, which Android
    // restores.
    private static final String STATE_SHOWING_FIAT = "showingFiat";

    boolean showingFiat = true;
    String fiatCurrency;
    String lastAddress;
    TextInputEditText receiveAmountInput;
    TextView conversionRate;
    Wallet wallet;
    Network network;
    LinearLayout receiveLayout;
    TextInputEditText label;
    TextInputEditText memo;
    long totalReceived;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_request);
        SystemBars.apply(this);

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
        network = Settings.get(this).getNetwork();
        Networks.showBadge(this, network);

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
        if (!network.hasFiat) {
            // Test coins have no price, so amounts are entered in BCH only.
            showingFiat = false;
            symbolLabel.setText("₿");
            toggle.setVisibility(View.GONE);
            conversionRate.setVisibility(View.GONE);
        } else if (savedInstanceState != null
                && !savedInstanceState.getBoolean(STATE_SHOWING_FIAT, true)) {
            showingFiat = false;
            symbolLabel.setText("₿");
        }

        receiveAmountInput.addTextChangedListener(new TextWatcher() {

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
            }
        });

        Button requestButton = findViewById(R.id.requestBtn);
        requestButton.setOnClickListener(v -> {
            totalReceived = 0;
            displayQRPopup();
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_SHOWING_FIAT, showingFiat);
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
            String amt = receiveAmountInput.getText().toString().replace(",", ".");
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
                String amt = receiveAmountInput.getText().toString().replace(",", ".");
                if (!amt.equals("")) {
                    try {
                        Amount bchRate = new Amount(Double.valueOf(amt));
                        receiveAmountInput.setText(ExchangeRates.getInstance().getAmountInFiat(bchRate, Currency.getInstance(fiatCurrency)));
                    } catch (Exception e) {
                        e.printStackTrace();
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
        Editable receiveAmountInputText = receiveAmountInput.getText();
        if (receiveAmountInputText == null || !network.hasFiat) {
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
        View customView = layoutInflater.inflate(R.layout.requestpopup, receiveLayout, false);
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

            // A request without an amount, as for the field's 0, lets the payer choose it.
            Amount requestAmount = null;
            if (receiveAmountInput.getText() != null && !receiveAmountInput.getText().toString().equals("")) {
                double bchAmount = Double.valueOf(receiveAmountInput.getText().toString().replace(",", "."));
                if (showingFiat) {
                    bchAmount = ExchangeRates.getInstance().convertToBCH(bchAmount, Currency.getInstance(fiatCurrency));
                }
                Amount amount = new Amount(bchAmount);
                if (amount.getSatoshis() > 0) {
                    requestAmount = amount;
                    builder.amount(amount.toBCH());
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

            Bitmap bitmap = QrCodes.encode(uri.getURI(), smallerDimension);
            ImageView qrImage = customView.findViewById(R.id.requestQRCode);

            qrImage.setImageBitmap(bitmap);

            View.OnClickListener onClickListener = v -> copyToClipboard(uri.getURI());

            qrImage.setOnClickListener(onClickListener);

            TextView addrText = customView.findViewById(R.id.requestURi);
            addrText.setText(uri.getURI());
            addrText.setOnClickListener(onClickListener);

            TextView helperText = customView.findViewById(R.id.uriHelpText);
            helperText.setText(requestAmount != null ? getString(R.string.send_bch_to, requestAmount.toString())
                    : getString(R.string.send_any_bch_to));

            wallet.listenAddress(addr, new AddressListener() {
                @Override
                public void onPaymentReceived(long amount) {
                    if (getApplicationContext() != null) {
                        runOnUiThread(() -> {
                            totalReceived += amount;
                            if (compareAmount == null || totalReceived >= compareAmount.getSatoshis()) {
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

                                Bitmap newBitmap = null;
                                try {
                                    newBitmap = QrCodes.encode(newUri.getURI(), finalDimension);
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
        final ClipboardManager clipboardManager = (ClipboardManager) clipboardService;
        if (clipboardManager != null) {
            ClipData clipData = ClipData.newPlainText(getString(R.string.source_text), data);
            clipboardManager.setPrimaryClip(clipData);
            Snackbar snackbar = Snackbar.make(receiveLayout, R.string.uri_copied_to_clipboard, Snackbar.LENGTH_LONG);
            snackbar.show();
        }
    }
}
