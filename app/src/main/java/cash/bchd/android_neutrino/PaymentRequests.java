package cash.bchd.android_neutrino;

import android.app.Activity;
import android.content.Intent;
import android.view.View;

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
import java.util.concurrent.Executors;

import cash.bchd.android_neutrino.wallet.Amount;
import cash.bchd.android_neutrino.wallet.ExchangeRates;
import cash.bchd.android_neutrino.wallet.Wallet;
import walletrpc.Api;

/**
 * PaymentRequests pays the payment request of a URI with an r parameter. The wallet downloads
 * the merchant's request, with BitPay's JSON Payment Protocol or BIP70, and creates the
 * transaction that pays it, off the UI thread since the download can take seconds, and the
 * confirmation screen then shows the payment.
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
     * does on the layout, and then starts the confirmation screen from the activity, or shows on
     * the layout why it can't.
     */
    static void pay(Activity activity, View layout, Wallet wallet, String uri, String fiatCurrency, int feePerByte) {
        Snackbar loading = Snackbar.make(layout, R.string.loading_payment_request, Snackbar.LENGTH_INDEFINITE);
        loading.show();
        ListenableFuture<Intent> confirmation = background.submit(() -> confirmation(activity, wallet, uri, fiatCurrency, feePerByte));
        Futures.addCallback(confirmation, new FutureCallback<Intent>() {
            @Override
            public void onSuccess(Intent intent) {
                loading.dismiss();
                if (!activity.isFinishing() && !activity.isDestroyed()) {
                    // For its result, which closes the send screen once the payment is sent.
                    activity.startActivityForResult(intent, ConfirmationActivity.RC_PAY);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                int message = t instanceof InsufficientFundsException ? R.string.insufficient_funds : R.string.invalid_payment_request;
                Snackbar.make(layout, message, Snackbar.LENGTH_LONG).show();
                t.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(activity));
    }

    /**
     * confirmation downloads the URI's payment request, creates the transaction that pays it and
     * returns the intent that shows the payment on the confirmation screen.
     */
    private static Intent confirmation(Activity activity, Wallet wallet, String uri, String fiatCurrency, int feePerByte) throws Exception {
        Api.DownloadPaymentRequestResponse pr = wallet.downloadPaymentRequest(uri);
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
