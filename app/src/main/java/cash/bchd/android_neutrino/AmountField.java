package cash.bchd.android_neutrino;

import android.content.Context;
import android.text.Editable;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.util.Currency;

import cash.bchd.android_neutrino.wallet.Amount;
import cash.bchd.android_neutrino.wallet.ExchangeRates;
import cash.bchd.android_neutrino.wallet.Network;

/**
 * AmountField is an amount entered in BCH or in the fiat currency, which a toggle switches
 * between, converting what was entered, with what it's worth in the other shown under it, as the
 * send and receive screens have it. Test coins have no price, so their amounts are in BCH only.
 */
final class AmountField {

    private final Context context;
    private final EditText input;
    private final TextView symbol;
    private final TextView conversion;
    private final boolean hasFiat;
    private final String fiatCurrency;
    private boolean showingFiat;

    AmountField(Context context, EditText input, TextView symbol, View toggle, TextView conversion,
                Network network, String fiatCurrency, boolean showingFiat) {
        this.context = context;
        this.input = input;
        this.symbol = symbol;
        this.conversion = conversion;
        this.hasFiat = network.hasFiat;
        this.fiatCurrency = fiatCurrency;
        this.showingFiat = showingFiat && hasFiat;
        if (!hasFiat) {
            toggle.setVisibility(View.GONE);
            conversion.setVisibility(View.GONE);
        }
        showSymbol();
    }

    boolean showingFiat() {
        return showingFiat;
    }

    /**
     * toggle switches the amount to the other unit, converting what was entered.
     */
    void toggle() {
        Editable text = input.getText();
        if (text == null) {
            return;
        }
        String amt = text.toString().replace(",", ".");
        if (showingFiat) {
            if (!amt.isEmpty()) {
                try {
                    Amount bch = new Amount(ExchangeRates.getInstance().convertToBCH(Double.valueOf(amt), currency()));
                    input.setText(bch.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else if (!amt.isEmpty()) {
            try {
                input.setText(ExchangeRates.getInstance().getAmountInFiat(new Amount(Double.valueOf(amt)), currency()));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        showingFiat = !showingFiat;
        showSymbol();
        updateConversion();
    }

    /**
     * showBch switches the amount to BCH, if it's in the fiat currency.
     */
    void showBch() {
        if (showingFiat) {
            toggle();
        }
    }

    /**
     * updateConversion shows what the amount entered is worth in the other unit.
     */
    void updateConversion() {
        Editable text = input.getText();
        if (text == null || !hasFiat) {
            return;
        }
        if (SendActivity.considerAmountTextZero(text)) {
            conversion.setText(showingFiat ? context.getString(R.string.bch_amount, "0") : currency().getSymbol() + "0");
            return;
        }
        double amount = Double.valueOf(text.toString().replace(",", "."));
        try {
            if (showingFiat) {
                Amount bch = new Amount(ExchangeRates.getInstance().convertToBCH(amount, currency()));
                conversion.setText(context.getString(R.string.bch_amount, bch.toString()));
            } else {
                conversion.setText(ExchangeRates.getInstance().getFormattedAmountInFiat(new Amount(amount), currency()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showSymbol() {
        symbol.setText(showingFiat ? currency().getSymbol() : "₿");
    }

    private Currency currency() {
        return Currency.getInstance(fiatCurrency);
    }
}
