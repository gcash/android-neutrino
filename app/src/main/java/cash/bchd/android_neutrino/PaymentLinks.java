package cash.bchd.android_neutrino;

import android.content.Context;
import android.view.View;

import com.google.android.material.snackbar.Snackbar;

import cash.bchd.android_neutrino.wallet.BitcoinPaymentURI;

/**
 * PaymentLinks refuses the payment links that the wallet can't pay as they ask: one that asks for
 * CashTokens, which it can't send yet, and one that requires a parameter it doesn't understand,
 * which BIP 21 has refused. Paying such a link with the BCH it names would pay something else.
 */
final class PaymentLinks {

    private PaymentLinks() {
    }

    /**
     * refuse shows on the layout why the wallet can't pay the link, if it can't, and returns
     * whether it can't.
     */
    static boolean refuse(View layout, BitcoinPaymentURI uri) {
        Context context = layout.getContext();
        String reason;
        String required = uri.getRequiredParameter();
        if (uri.requestsTokens()) {
            reason = context.getString(R.string.payment_link_tokens);
        } else if (required != null) {
            reason = context.getString(R.string.payment_link_required_parameter, required);
        } else {
            return false;
        }
        Snackbar snackbar = Snackbar.make(layout, reason, Snackbar.LENGTH_LONG);
        snackbar.setTextMaxLines(5);
        snackbar.show();
        return true;
    }
}
