package cash.bchd.android_neutrino.wallet;

import java.math.BigDecimal;

public class Amount {

    private static final double SATOSHIS_PER_BCH = 100000000;
    private long satoshis;

    public Amount(long satoshis) {
        this.satoshis = satoshis;
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
        BigDecimal stripedVal = new BigDecimal(s).stripTrailingZeros();
        return stripedVal.toString();
    }
}
