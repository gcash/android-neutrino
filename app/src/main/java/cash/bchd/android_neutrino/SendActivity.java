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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import cash.bchd.android_neutrino.wallet.Amount;
import cash.bchd.android_neutrino.wallet.BitcoinPaymentURI;
import cash.bchd.android_neutrino.wallet.ExchangeRates;
import cash.bchd.android_neutrino.wallet.Network;
import cash.bchd.android_neutrino.wallet.TokensHeld;
import cash.bchd.android_neutrino.wallet.Wallet;
import io.grpc.Status;
import walletrpc.Api;

public class SendActivity extends AppCompatActivity {

    // What a recreated screen needs besides the fields, which Android restores: the unit the
    // amount is in, and the label of the payment link that filled them in.
    private static final String STATE_SHOWING_FIAT = "showingFiat";
    private static final String STATE_LABEL = "label";

    // The scanner, whose QR code fills in the payment.
    private final ActivityResultLauncher<Void> scanner =
            registerForActivityResult(new ScannerActivity.Scan(), qrdata -> {
                if (qrdata != null) {
                    fillIn(qrdata);
                }
            });
    // The confirmation screen, which closes this screen too once the payment is sent.
    private final ActivityResultLauncher<Intent> confirmationScreen =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    finish();
                }
            });

    // Checks the address off the main thread, one check at a time, as the wallet answers over
    // gRPC, and shows only the result for the address as it stands.
    private final ExecutorService addressChecks = Executors.newSingleThreadExecutor();
    private final AtomicInteger addressEdits = new AtomicInteger();
    // Asks the wallet for the balance and builds the payment off the main thread, as a busy
    // wallet answers late, and the screen mustn't wait on it.
    private final ListeningExecutorService walletCalls =
            MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
    private ListenableFuture<Long> balanceRequest;
    // Whether the wallet is building the payment, which Send waits for.
    private boolean preparing;
    private Button sendButton;
    private ProgressBar sendProgress;
    private ToggleButton sendAllBtn;
    private TextInputLayout addressLayout;
    // How long to wait before asking a wallet that failed to answer for the balance again.
    private static final long BALANCE_RETRY_MILLIS = 1000;
    Network network;
    TextView balanceTxtView;
    Amount balance;
    String fiatCurrency;
    TextInputEditText inputAmount;
    AmountField amountField;
    boolean sendAll;
    LinearLayout sendLayout;
    TextInputEditText address;
    TextInputEditText memo;
    String label;
    int satPerByte;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send);
        SystemBars.apply(this);

        sendLayout = findViewById(R.id.sendLayout);

        Intent intent = getIntent();

        fiatCurrency = intent.getExtras().getString("fiatCurrency");
        satPerByte = intent.getExtras().getInt("feePerByte");

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

        inputAmount = findViewById(R.id.amountInput);
        ImageView toggle = findViewById(R.id.toggleImage);
        amountField = new AmountField(this, inputAmount, findViewById(R.id.symbolLabel), toggle,
                findViewById(R.id.conversionRate), network, fiatCurrency,
                savedInstanceState == null || savedInstanceState.getBoolean(STATE_SHOWING_FIAT, true));
        toggle.setOnClickListener(v -> toggleBchFiat());
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
                amountField.updateConversion();
                if (inputAmount.getText() != null && !inputAmount.getText().toString().equals("")) {
                    TextInputEditText etAmount = findViewById(R.id.amountInput);
                    etAmount.setError(null);
                }
            }
        });

        if (savedInstanceState != null) {
            label = savedInstanceState.getString(STATE_LABEL);
        }
        showBalance();

        address = findViewById(R.id.addressInput);
        addressLayout = findViewById(R.id.addressLayout);
        addressLayout.setEndIconOnClickListener(v -> scanner.launch(null));

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
                checkAddress(false);
            }
        });

        sendButton = findViewById(R.id.sendBtn);
        sendProgress = findViewById(R.id.sendProgress);
        sendButton.setOnClickListener(v -> send());

        sendAllBtn = findViewById(R.id.sendAllBtn);
        sendAllBtn.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                amountField.showBch();
                showBalance();
                // Until the balance comes, as on a restored screen, which fills it in then.
                inputAmount.setText(balance != null ? balance.toString() : "");
                inputAmount.setEnabled(false);
                amountField.updateConversion();
                sendAll = true;
                warnOfTokens();
            } else {
                inputAmount.setText("");
                amountField.updateConversion();
                sendAll = false;
                inputAmount.setEnabled(true);
            }
        });

        askBalance();

        // The link or payment request the screen was opened for is handled once: a recreated
        // screen has the fields it filled in, or went on to the confirmation screen.
        if (savedInstanceState != null) {
            return;
        }

        String qrData = intent.getExtras().getString("qrdata");
        if (qrData != null) {
            fillIn(qrData);
            return;
        }

        byte[] paymentRequest = intent.getByteArrayExtra(PaymentRequestFile.EXTRA);
        if (paymentRequest != null) {
            Wallet wallet = Wallet.getInstance();
            if (wallet == null) {
                Snackbar.make(sendLayout, R.string.wallet_is_not_loaded_yet, Snackbar.LENGTH_LONG).show();
                return;
            }
            PaymentRequests.payFile(this, sendLayout, wallet, paymentRequest, fiatCurrency, satPerByte,
                    confirmationScreen);
            return;
        }

        Uri data = getIntent().getData();
        if (data != null) {
            fillIn(data.toString());
            return;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        WalletStarter.restartIfGone(this);
    }

    // showBalance shows the balance in the unit the amount is in, or that the wallet is still asked.
    private void showBalance() {
        if (balance == null) {
            balanceTxtView.setText(R.string.balance_loading);
            return;
        }
        if (amountField.showingFiat()) {
            try {
                balanceTxtView.setText(getString(R.string.balance_fiat_amount, ExchangeRates.getInstance().getFormattedAmountInFiat(balance, Currency.getInstance(fiatCurrency))));
                return;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        balanceTxtView.setText(getString(R.string.balance_bch_amount, balance.toString()));
    }

    // askBalance asks the wallet that runs, once one does, for the balance off the main thread, and
    // shows it when it comes. Send and Send All wait for it, as both need it.
    private void askBalance() {
        ListenableFuture<Long> request = Futures.transformAsync(Wallet.whenRunning(),
                wallet -> walletCalls.submit(wallet::balance), MoreExecutors.directExecutor());
        balanceRequest = request;
        updateSendable();
        Futures.addCallback(request, new FutureCallback<Long>() {
            @Override
            public void onSuccess(Long satoshis) {
                if (isDestroyed()) {
                    return;
                }
                balance = new Amount(satoshis);
                showBalance();
                if (sendAll) {
                    inputAmount.setText(balance.toString());
                    amountField.updateConversion();
                }
                updateSendable();
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                if (t instanceof CancellationException || isDestroyed()) {
                    return;
                }
                // The wallet stopped as it was asked, so the next one that runs is.
                t.printStackTrace();
                sendLayout.postDelayed(() -> {
                    if (!isDestroyed()) {
                        askBalance();
                    }
                }, BALANCE_RETRY_MILLIS);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // updateSendable has Send wait for the balance and for a payment being built, with its spinner
    // on meanwhile, and Send All wait for the balance.
    private void updateSendable() {
        sendButton.setEnabled(balance != null && !preparing);
        sendProgress.setVisibility(preparing ? View.VISIBLE : View.GONE);
        sendAllBtn.setEnabled(balance != null);
    }

    // send checks what it can at once, then has the wallet check the address and build the payment
    // off the main thread, and opens the confirmation screen with it.
    private void send() {
        if (preparing || balance == null) {
            return;
        }
        // The wallet that runs now, as the one that ran when the screen opened may have stopped
        // since, and started again.
        Wallet wallet = Wallet.getInstance();
        if (wallet == null || !wallet.isRunning()) {
            Snackbar.make(sendLayout, R.string.wallet_is_not_loaded_yet, Snackbar.LENGTH_LONG).show();
            return;
        }
        TextInputEditText etAmount = findViewById(R.id.amountInput);
        Amount toSpend = null;
        try {
            if (inputAmount.getText() != null && !inputAmount.getText().toString().equals("")) {
                if (amountField.showingFiat()) {
                    double fiatAmount = Double.valueOf(inputAmount.getText().toString().replace(",", "."));
                    toSpend = new Amount(ExchangeRates.getInstance().convertToBCH(fiatAmount, Currency.getInstance(fiatCurrency)));
                } else {
                    toSpend = Amount.fromBCH(inputAmount.getText().toString());
                }
            }
        } catch (Exception e) {
            toSpend = null;
        }
        if (toSpend == null || toSpend.getSatoshis() == 0 || toSpend.getSatoshis() > balance.getSatoshis()) {
            etAmount.setError(getString(R.string.invalid_amount));
            // The address is told of too, as it's checked as it's typed.
            checkAddress(true);
            return;
        }

        String to = address.getText() == null ? "" : address.getText().toString();
        boolean all = sendAll;
        Amount spend = toSpend;
        String memoText = memo.getText() == null ? "" : memo.getText().toString();
        preparing = true;
        updateSendable();
        ListenableFuture<Intent> built = walletCalls.submit(() -> {
            if (!wallet.validateAddress(to)) {
                return null;
            }
            byte[] serializedTx;
            long txFee;
            List<Long> inputVals;
            if (all) {
                Api.SweepAccountResponse tx = wallet.sweepAccount(to, satPerByte);
                serializedTx = tx.getSerializedTransaction().toByteArray();
                txFee = tx.getFee();
                inputVals = tx.getInputValuesList();
            } else {
                Api.CreateTransactionResponse tx = wallet.createTransaction(to, spend.getSatoshis(), satPerByte);
                serializedTx = tx.getSerializedTransaction().toByteArray();
                txFee = tx.getFee();
                inputVals = tx.getInputValuesList();
            }
            String fiatFormatted = network.hasFiat ? ExchangeRates.getInstance().getFormattedAmountInFiat(spend, Currency.getInstance(fiatCurrency)) : "";
            ArrayList<String> inputStrings = new ArrayList<>();
            for (Long val : inputVals) {
                inputStrings.add(String.valueOf(val));
            }

            Intent confirm = new Intent(getApplicationContext(), ConfirmationActivity.class);
            confirm.putExtra("paymentAddress", to);
            confirm.putExtra("amountBCH", spend.toString());
            confirm.putExtra("amountFiat", fiatFormatted);
            confirm.putExtra("fee", txFee);
            confirm.putExtra("serializedTransaction", serializedTx);
            confirm.putStringArrayListExtra("inputVals", inputStrings);
            if (!memoText.equals("")) {
                confirm.putExtra("memo", memoText);
            }
            if (label != null && !label.equals("")) {
                confirm.putExtra("label", label);
            }
            return confirm;
        });
        Futures.addCallback(built, new FutureCallback<Intent>() {
            @Override
            public void onSuccess(Intent confirm) {
                preparing = false;
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                updateSendable();
                if (confirm == null) {
                    addressLayout.setError(getString(R.string.invalid_bitcoin_cash_address));
                    return;
                }
                addressLayout.setError(null);
                confirmationScreen.launch(confirm);
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                preparing = false;
                if (t instanceof CancellationException || isFinishing() || isDestroyed()) {
                    return;
                }
                updateSendable();
                // Say that the wallet can't be reached, as when it stopped or is starting again,
                // or why bchwallet refused the transaction, such as a dust amount, when it did.
                String message;
                if (Status.fromThrowable(t).getCode() == Status.Code.UNAVAILABLE) {
                    message = getString(R.string.wallet_is_not_loaded_yet);
                } else {
                    String reason = Wallet.reasonFor(t);
                    message = reason != null
                            ? getString(R.string.error_creating_transaction_reason, reason)
                            : getString(R.string.error_creating_transaction);
                }
                Snackbar snackbar = Snackbar.make(sendLayout, message, Snackbar.LENGTH_LONG);
                snackbar.setTextMaxLines(5);
                snackbar.show();
                t.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // toggleBchFiat switches the amount to the other unit, and the balance with it.
    private void toggleBchFiat() {
        amountField.toggle();
        showBalance();
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // fillIn fills in the payment from a payment link or address, as a QR code or NFC tag has it,
    // or pays the link's payment request.
    private void fillIn(String qrdata) {
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
            if (PaymentLinks.refuse(sendLayout, uri)) {
                return;
            }
            if (uri.getR() != null) {
                processPaymentRequest(qrdata);
                return;
            }

            address.setText(uri.getAddress());
            if (uri.getAmount() != null) {
                amountField.showBch();
                showBalance();
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
        checkAddress(true);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_SHOWING_FIAT, amountField.showingFiat());
        outState.putString(STATE_LABEL, label);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
            fillIn(intent.getDataString());
        }
    }

    // warnOfTokens tells that Send All leaves the wallet's CashTokens here, with the BCH they
    // carry, when it holds any, as the app can't send them yet, so that the wallet isn't taken
    // for empty.
    private void warnOfTokens() {
        Wallet wallet = Wallet.getInstance();
        if (wallet == null) {
            return;
        }
        Futures.addCallback(wallet.tokensHeldAsync(), new FutureCallback<TokensHeld>() {
            @Override
            public void onSuccess(TokensHeld held) {
                // A dialog on a screen that's gone, as one a rotation replaced, crashes.
                if (!held.any() || !sendAll || isFinishing() || isDestroyed()) {
                    return;
                }
                new AlertDialog.Builder(SendActivity.this)
                        .setTitle(R.string.tokens_held_title)
                        .setMessage(getString(R.string.send_all_leaves_tokens, new Amount(held.satoshis).toString()))
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                t.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void processPaymentRequest(String qrdata) {
        Wallet wallet = Wallet.getInstance();
        if (wallet == null) {
            Snackbar.make(sendLayout, R.string.wallet_is_not_loaded_yet, Snackbar.LENGTH_LONG).show();
            return;
        }
        PaymentRequests.pay(this, sendLayout, wallet, qrdata, fiatCurrency, satPerByte, confirmationScreen);
    }

    /**
     * checkAddress has the wallet check the address in the field, off the main thread, and
     * clears the field's error when it's valid, or, with showInvalid, shows it when it isn't, as
     * for an address filled in whole rather than typed. A check that a later edit overtook is
     * skipped, or its result dropped.
     */
    private void checkAddress(boolean showInvalid) {
        int edit = addressEdits.incrementAndGet();
        String text = address.getText() == null ? "" : address.getText().toString();
        addressChecks.execute(() -> {
            if (edit != addressEdits.get()) {
                return;
            }
            boolean valid;
            try {
                Wallet wallet = Wallet.getInstance();
                if (wallet == null) {
                    return;
                }
                valid = wallet.validateAddress(text);
            } catch (Exception e) {
                // The wallet is starting or stopping, and the send button checks again.
                return;
            }
            runOnUiThread(() -> {
                if (edit != addressEdits.get() || isFinishing()) {
                    return;
                }
                TextInputLayout layout = findViewById(R.id.addressLayout);
                if (valid) {
                    layout.setError(null);
                } else if (showInvalid) {
                    layout.setError(getString(R.string.invalid_bitcoin_cash_address));
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        addressChecks.shutdownNow();
        if (balanceRequest != null) {
            balanceRequest.cancel(true);
        }
        walletCalls.shutdownNow();
        super.onDestroy();
    }

    static boolean considerAmountTextZero(Editable inputAmountText) {
        return inputAmountText.toString().equals("") || inputAmountText.toString().equals(".") || inputAmountText.toString().equals(",");
    }
}