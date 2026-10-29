package cash.bchd.android_neutrino;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import cash.bchd.android_neutrino.wallet.Amount;
import cash.bchd.android_neutrino.wallet.ExchangeRates;
import cash.bchd.android_neutrino.wallet.Wallet;
import io.grpc.Status;
import walletrpc.Api;

/**
 * PaymentRequests pays the payment request of a URI with an r parameter, or a BIP70 one that a
 * merchant handed over as a file. The wallet downloads the merchant's request, with BitPay's JSON
 * Payment Protocol or BIP70, or checks the file, and creates the transaction that pays it, off
 * the UI thread since the download can take seconds, and the confirmation screen then shows the
 * payment.
 */
final class PaymentRequests {

    // Prepares one payment request at a time.
    private static final ListeningExecutorService background =
            MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());

    private PaymentRequests() {
    }

    private static final class InsufficientFundsException extends Exception {
    }

    /**
     * pay prepares the payment of the URI's payment request in the background, showing that it
     * does on the layout, and then opens the confirmation screen with the activity's launcher, or
     * shows on the layout why it can't.
     */
    static void pay(Activity activity, View layout, Wallet wallet, String uri, String fiatCurrency, int feePerByte,
                    ActivityResultLauncher<Intent> confirmationScreen) {
        prepare(activity, layout, confirmationScreen, () -> confirmation(activity, wallet,
                wallet.downloadPaymentRequest(uri), fiatCurrency, feePerByte));
    }

    /**
     * payFile is pay for a BIP70 payment request that the app has as a file, which the wallet
     * checks as it checks a downloaded one.
     */
    static void payFile(Activity activity, View layout, Wallet wallet, byte[] paymentRequest, String fiatCurrency,
                        int feePerByte, ActivityResultLauncher<Intent> confirmationScreen) {
        prepare(activity, layout, confirmationScreen, () -> confirmation(activity, wallet,
                wallet.parsePaymentRequest(paymentRequest), fiatCurrency, feePerByte));
    }

    private static void prepare(Activity activity, View layout, ActivityResultLauncher<Intent> confirmationScreen,
                                Callable<Intent> prepareConfirmation) {
        Snackbar loading = Snackbar.make(layout, R.string.loading_payment_request, Snackbar.LENGTH_INDEFINITE);
        loading.show();
        ListenableFuture<Intent> confirmation = background.submit(prepareConfirmation);
        Futures.addCallback(confirmation, new FutureCallback<Intent>() {
            @Override
            public void onSuccess(Intent intent) {
                loading.dismiss();
                if (!activity.isFinishing() && !activity.isDestroyed()) {
                    confirmationScreen.launch(intent);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                loading.dismiss();
                Snackbar snackbar = Snackbar.make(layout, failure(activity, t), Snackbar.LENGTH_LONG);
                snackbar.setTextMaxLines(5);
                snackbar.show();
                t.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(activity));
    }

    // failure returns why a payment request can't be paid: the wallet's reason for refusing it,
    // such as an expired request or an untrusted certificate, when it gave one.
    private static String failure(Context context, Throwable t) {
        if (t instanceof InsufficientFundsException) {
            return context.getString(R.string.insufficient_funds);
        }
        String reason = Status.fromThrowable(t).getDescription();
        if (reason == null || reason.isEmpty()) {
            return context.getString(R.string.invalid_payment_request);
        }
        return context.getString(R.string.invalid_payment_request_reason, reason);
    }

    /**
     * confirmation creates the transaction that pays a checked payment request and returns the
     * intent that shows the payment on the confirmation screen.
     */
    private static Intent confirmation(Activity activity, Wallet wallet, Api.DownloadPaymentRequestResponse pr,
                                       String fiatCurrency, int feePerByte) throws Exception {
        List<Api.CreateTransactionRequest.Output> outputs = new ArrayList<>();
        long totalSatoshis = 0;
        for (Api.DownloadPaymentRequestResponse.Output out : pr.getOutputsList()) {
            Api.CreateTransactionRequest.Output output = Api.CreateTransactionRequest.Output.newBuilder().setAmount(out.getAmount()).setAddress(out.getAddress()).build();
            outputs.add(output);
            totalSatoshis += out.getAmount();
        }
        Amount totalAmt = new Amount(totalSatoshis);
        String fiatFormatted = wallet.getNetwork().hasFiat
                ? ExchangeRates.getInstance().getFormattedAmountInFiat(totalAmt, Currency.getInstance(fiatCurrency)) : "";
        if (totalSatoshis > wallet.balance()) {
            throw new InsufficientFundsException();
        }

        Api.CreateTransactionResponse tx = wallet.createTransaction(outputs, satPerKbFee(feePerByte, pr.getRequiredSatPerKbFee()));
        ArrayList<String> inputStrings = new ArrayList<>();
        for (Long val : tx.getInputValuesList()) {
            inputStrings.add(String.valueOf(val));
        }

        Intent intent = new Intent(activity.getApplicationContext(), ConfirmationActivity.class);
        intent.putExtra("paymentAddress", outputs.get(0).getAddress());
        intent.putExtra("amountBCH", totalAmt.toString());
        intent.putExtra("amountFiat", fiatFormatted);
        intent.putExtra("fee", tx.getFee());
        intent.putExtra("serializedTransaction", tx.getSerializedTransaction().toByteArray());
        intent.putStringArrayListExtra("inputVals", inputStrings);
        intent.putExtra("memo", pr.getMemo());
        intent.putExtra("label", pr.getPayToName());

        intent.putExtra("isPaymentRequest", true);
        intent.putExtra("paymentProtocol", pr.getProtocolValue());
        intent.putExtra("merchantData", pr.getMerchantData().toByteArray());
        intent.putExtra("paymentURL", pr.getPaymentUrl());
        intent.putExtra("refundAddress", wallet.currentAddress());
        intent.putExtra("refundAmount", totalSatoshis);
        return intent;
    }

    /**
     * satPerKbFee returns the fee rate in satoshis per kB to pay a payment request with: the
     * user's rate, or the rate the merchant requires if that is higher, since the merchant
     * refuses a payment below it. The required rate and the result are uint32s, as in the API.
     */
    static int satPerKbFee(int feePerByte, int requiredSatPerKb) {
        return (int) Math.max(feePerByte * 1000L, Integer.toUnsignedLong(requiredSatPerKb));
    }
}
