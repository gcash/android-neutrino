package cash.bchd.android_neutrino.wallet;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormatSymbols;

public class Amount {

    private static final double SATOSHIS_PER_BCH = 100000000;
    private long satoshis;

    public Amount(long satoshis) {
        this.satoshis = satoshis;
    }

    public Amount(double bch) {
        // Round, since a decimal amount is rarely exact as a double: 0.03346
        // BCH times 1e8 is 3345999.9999999995, which truncating made one
        // satoshi short.
        this.satoshis = Math.round(bch * SATOSHIS_PER_BCH);
    }

    /**
     * Parse an amount of BCH as typed, with a period or a comma as the decimal separator.
     * Decimals beyond satoshis are rounded.
     */
    public static Amount fromBCH(String text) {
        BigDecimal bch = new BigDecimal(text.trim().replace(",", "."));
        return new Amount(bch.movePointRight(8).setScale(0, RoundingMode.HALF_UP).longValueExact());
    }

    public double toBCH() {
        Long sats = Long.valueOf(this.satoshis);
        return sats.doubleValue() / SATOSHIS_PER_BCH;
    }

    /**
     * toString writes the amount in BCH, exactly and without trailing zeros, as the app shows
     * amounts and reads them back.
     */
    public String toString() {
        if (this.satoshis == 0) {
            return "0";
        }
        return localize(BigDecimal.valueOf(this.satoshis, 8).stripTrailingZeros().toPlainString());
    }

    /**
     * localize writes a plain decimal number, such as 0.5, with a comma for its point where the
     * phone's language writes decimals with one, which Amount.fromBCH and the fiat amount
     * fields read back. Its digits stay 0 to 9, which formatting in the phone's language would
     * write in another script for some languages, such as Arabic and Persian.
     */
    static String localize(String plain) {
        return DecimalFormatSymbols.getInstance().getDecimalSeparator() == ',' ? plain.replace('.', ',') : plain;
    }

    public long getSatoshis() {
        return this.satoshis;
    }
}
