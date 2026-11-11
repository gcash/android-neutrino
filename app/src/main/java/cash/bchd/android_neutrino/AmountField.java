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
 * Without an exchange rate, as before the rates came, an amount is in BCH only too: the toggle is
 * dimmed and won't switch, and an amount it can't convert isn't taken as one in the other unit.
 */
final class AmountField {

    private final Context context;
    private final EditText input;
    private final TextView symbol;
    private final View toggle;
    private final TextView conversion;
    private final boolean hasFiat;
    private final String fiatCurrency;
    private boolean showingFiat;

    AmountField(Context context, EditText input, TextView symbol, View toggle, TextView conversion,
                Network network, String fiatCurrency, boolean showingFiat) {
        this.context = context;
        this.input = input;
        this.symbol = symbol;
        this.toggle = toggle;
        this.conversion = conversion;
        this.hasFiat = network.hasFiat;
        this.fiatCurrency = fiatCurrency;
        if (hasFiat) {
            // Also for a screen Android brought back without the main screen, which starts them.
            ExchangeRates.start();
        } else {
            toggle.setVisibility(View.GONE);
            conversion.setVisibility(View.GONE);
        }
        this.showingFiat = showingFiat && hasFiat && hasRate();
        showSymbol();
        showToggle();
        // What it's worth in the other unit, rather than what the layout has until it changes.
        updateConversion();
    }

    /**
     * hasRate returns whether there is an exchange rate for the fiat currency yet.
     */
    boolean hasRate() {
        return ExchangeRates.hasRate(currency());
    }

    boolean showingFiat() {
        return showingFiat;
    }

    /**
     * toggle switches the amount to the other unit, converting what was entered, and returns
     * false if it can't, as to the fiat currency without a rate for it.
     */
    boolean toggle() {
        Editable text = input.getText();
        if (text == null) {
            return false;
        }
        if (!showingFiat && !hasRate()) {
            showToggle();
            return false;
        }
        String amt = text.toString().replace(",", ".");
        if (showingFiat) {
            if (!amt.isEmpty()) {
                try {
                    Amount bch = new Amount(ExchangeRates.getInstance().convertToBCH(Double.valueOf(amt), currency()));
                    input.setText(bch.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                    // An amount in the fiat currency is no amount in BCH.
                    input.setText("");
                }
            }
        } else if (!amt.isEmpty()) {
            try {
                input.setText(ExchangeRates.getInstance().getAmountInFiat(new Amount(Double.valueOf(amt)), currency()));
            } catch (Exception e) {
                e.printStackTrace();
                input.setText("");
            }
        }
        showingFiat = !showingFiat;
        showSymbol();
        updateConversion();
        return true;
    }

    /**
     * leaveFiatWithoutRate switches an amount in the fiat currency to BCH if there is no rate to
     * convert it with, clearing it, as when the currency chosen has none, and returns whether it
     * did, so that the amount is entered again, rather than taken as one it isn't.
     */
    boolean leaveFiatWithoutRate() {
        if (!showingFiat || hasRate()) {
            return false;
        }
        input.setText("");
        showingFiat = false;
        showSymbol();
        updateConversion();
        return true;
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
        showToggle();
        if (!hasRate()) {
            conversion.setText("");
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

    // showToggle dims the toggle while there is no rate to switch to the fiat currency with.
    private void showToggle() {
        toggle.setAlpha(showingFiat || hasRate() ? 1f : 0.4f);
    }

    private Currency currency() {
        return Currency.getInstance(fiatCurrency);
    }
}
