package cash.bchd.android_neutrino.wallet;

import java.math.BigDecimal;
import java.math.RoundingMode;

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

    public String toString() {
        if (this.satoshis == 0) {
            return "0";
        }
        return removeTrailingZeros(toBCH());
    }

    private static String removeTrailingZeros(double d) {
        String s = String.format("%.12f", d);
        boolean euroStyle = false;
        if (s.contains(",")) {
            euroStyle = true;
            s = s.replace(",", ".");
        }
        BigDecimal stripedVal = new BigDecimal(s).stripTrailingZeros();
        if (stripedVal.toString().contains("E")) {
            return String.format("%.0f", stripedVal);
        }
        if (euroStyle) {
            return stripedVal.toString().replace(".", ",");
        }
        return stripedVal.toString();
    }

    public long getSatoshis() {
        return this.satoshis;
    }
}
