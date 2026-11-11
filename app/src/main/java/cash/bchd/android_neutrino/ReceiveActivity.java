package cash.bchd.android_neutrino;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Currency;
import java.util.concurrent.CancellationException;

import cash.bchd.android_neutrino.wallet.AddressListener;
import cash.bchd.android_neutrino.wallet.Addresses;
import cash.bchd.android_neutrino.wallet.Amount;
import cash.bchd.android_neutrino.wallet.BitcoinPaymentURI;
import cash.bchd.android_neutrino.wallet.ExchangeRates;
import cash.bchd.android_neutrino.wallet.Network;
import cash.bchd.android_neutrino.wallet.Wallet;

public class ReceiveActivity extends AppCompatActivity {

    // A recreated screen needs the unit the amount is in besides the fields, which Android
    // restores, and shows the request's popup again with what was paid toward it.
    private static final String STATE_SHOWING_FIAT = "showingFiat";
    private static final String STATE_REQUEST_POPUP = "requestPopup";
    private static final String STATE_TOTAL_RECEIVED = "totalReceived";

    boolean showingFiat = true;
    String fiatCurrency;
    TextInputEditText receiveAmountInput;
    TextView conversionRate;
    Network network;
    LinearLayout receiveLayout;
    TextInputEditText label;
    TextInputEditText memo;
    long totalReceived;
    PopupWindow requestPopup;
    // The request for the address the popup shows, which closing the popup cancels.
    private ListenableFuture<Addresses> addressRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_request);
        SystemBars.apply(this);

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
        }
        receiveAmountInput = findViewById(R.id.receiveAmountInput);
        conversionRate = findViewById(R.id.receiveConversionRate);

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

        // The popup shows once the screen's window is there, and the fields it is made from are
        // restored.
        if (savedInstanceState != null && savedInstanceState.getBoolean(STATE_REQUEST_POPUP)) {
            totalReceived = savedInstanceState.getLong(STATE_TOTAL_RECEIVED);
            receiveLayout.post(this::displayQRPopup);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        WalletStarter.restartIfGone(this);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_SHOWING_FIAT, showingFiat);
        outState.putBoolean(STATE_REQUEST_POPUP, requestPopup != null && requestPopup.isShowing());
        outState.putLong(STATE_TOTAL_RECEIVED, totalReceived);
    }

    @Override
    protected void onDestroy() {
        // The popup goes with its window, which a recreated screen replaces.
        if (requestPopup != null) {
            requestPopup.dismiss();
        }
        super.onDestroy();
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

    // displayQRPopup shows the payment request in a popup: its URI and QR code, with the wallet's
    // first unused address, asked for off the main thread, as a busy wallet answers late, with a
    // spinner until it comes, and never one kept from before, which could have been paid.
    private void displayQRPopup() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int dimension = Math.min(metrics.widthPixels, metrics.heightPixels);

        // The request is made before the popup opens, which then shows it, or not at all, with an
        // amount that can't be read, as a fiat one before the rates came.
        BitcoinPaymentURI.Builder builder = new BitcoinPaymentURI.Builder();
        // A request without an amount, as for an empty field, lets the payer choose it.
        Amount requestAmount = null;
        try {
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
        } catch (Exception e) {
            e.printStackTrace();
            receiveAmountInput.setError(getString(R.string.invalid_amount));
            return;
        }

        if (label.getText() != null && !label.getText().toString().equals("")) {
            builder.label(label.getText().toString());
        }

        if (memo.getText() != null && !memo.getText().toString().equals("")) {
            builder.message(memo.getText().toString());
        }

        LayoutInflater layoutInflater = (LayoutInflater) ReceiveActivity.this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View customView = layoutInflater.inflate(R.layout.requestpopup, receiveLayout, false);
        PopupWindow popupWindow = new PopupWindow(customView, CoordinatorLayout.LayoutParams.WRAP_CONTENT, CoordinatorLayout.LayoutParams.WRAP_CONTENT, true);
        popupWindow.showAtLocation(findViewById(R.id.receiveLayout), Gravity.CENTER, 0, 0);
        requestPopup = popupWindow;
        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOnDismissListener(() -> {
            if (addressRequest != null) {
                addressRequest.cancel(true);
            }
        });

        TextView helperText = customView.findViewById(R.id.uriHelpText);
        helperText.setText(requestAmount != null ? getString(R.string.send_bch_to, requestAmount.toString())
                : getString(R.string.send_any_bch_to));

        askAddress(customView, popupWindow, builder, requestAmount, dimension);
    }

    // askAddress asks the wallet for its address, once one runs, and shows the request with it
    // when it comes, or says that it couldn't get it, which a tap asks again.
    private void askAddress(View popup, PopupWindow window, BitcoinPaymentURI.Builder builder,
                            Amount requestAmount, int dimension) {
        ProgressBar loading = popup.findViewById(R.id.requestQRLoading);
        ImageView qrImage = popup.findViewById(R.id.requestQRCode);
        TextView uriText = popup.findViewById(R.id.requestURi);
        loading.setVisibility(View.VISIBLE);
        qrImage.setImageDrawable(QrCodes.blank(dimension));
        qrImage.setOnClickListener(null);
        // As long as the request will be, unseen, so that the popup doesn't grow when it comes.
        uriText.setText(builder.address(MainActivity.ADDRESS_SIZED).build().getURI());
        uriText.setTextColor(Color.TRANSPARENT);
        uriText.setOnClickListener(null);

        ListenableFuture<Addresses> request = Wallet.currentAddressesAsync();
        addressRequest = request;
        Futures.addCallback(request, new FutureCallback<Addresses>() {
            @Override
            public void onSuccess(Addresses addresses) {
                if (!window.isShowing() || request != addressRequest) {
                    return;
                }
                loading.setVisibility(View.GONE);
                builder.address(addresses.plain);
                showRequest(popup, builder.build(), dimension);
                listenForPayment(popup, addresses.plain, builder, requestAmount, dimension);
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                if (t instanceof CancellationException || !window.isShowing() || request != addressRequest) {
                    return;
                }
                t.printStackTrace();
                loading.setVisibility(View.GONE);
                uriText.setTextColor(Color.BLACK);
                uriText.setText(R.string.address_unavailable);
                View.OnClickListener again = v -> askAddress(popup, window, builder, requestAmount, dimension);
                uriText.setOnClickListener(again);
                qrImage.setOnClickListener(again);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // showRequest shows the request's URI and its QR code, which tapping either copies.
    private void showRequest(View popup, BitcoinPaymentURI uri, int dimension) {
        ImageView qrImage = popup.findViewById(R.id.requestQRCode);
        try {
            qrImage.setImageBitmap(QrCodes.encode(uri.getURI(), dimension));
        } catch (Exception e) {
            e.printStackTrace();
        }
        View.OnClickListener copy = v -> copyToClipboard(uri.getURI());
        qrImage.setOnClickListener(copy);
        TextView uriText = popup.findViewById(R.id.requestURi);
        uriText.setTextColor(Color.BLACK);
        uriText.setText(uri.getURI());
        uriText.setOnClickListener(copy);
    }

    // listenForPayment celebrates a payment to the address that pays the request, or shows what's
    // left to pay of it.
    private void listenForPayment(View popup, String address, BitcoinPaymentURI.Builder builder,
                                  Amount requestAmount, int dimension) {
        Wallet wallet = Wallet.getInstance();
        if (wallet == null) {
            return;
        }
        wallet.listenAddress(address, new AddressListener() {
            @Override
            public void onPaymentReceived(long amount) {
                if (getApplicationContext() != null) {
                    runOnUiThread(() -> {
                        totalReceived += amount;
                        if (requestAmount == null || totalReceived >= requestAmount.getSatoshis()) {
                            Vibrations.buzz(getApplicationContext());
                            CoinFlip.show(popup.findViewById(R.id.requestQRLayout));
                        } else {
                            TextView partialPayment = popup.findViewById(R.id.parialPayment);
                            partialPayment.setVisibility(View.VISIBLE);

                            TextView helperText = popup.findViewById(R.id.uriHelpText);
                            Amount newAmt = new Amount(requestAmount.getSatoshis() - totalReceived);
                            helperText.setText(getString(R.string.send_bch_to, newAmt.toString()));

                            builder.amount(newAmt.toBCH());
                            showRequest(popup, builder.build(), dimension);

                            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) helperText.getLayoutParams();
                            params.setMargins(params.leftMargin, -25, params.rightMargin, params.bottomMargin);
                        }
                    });
                }
            }
        });
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
