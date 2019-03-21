package cash.bchd.android_neutrino.wallet;

import java.math.BigDecimal;

public class Amount {

    private static final double SATOSHIS_PER_BCH = 100000000;
    private long satoshis;

    public Amount(long satoshis) {
        this.satoshis = satoshis;
    }

    public Amount(double bch) {
        this.satoshis = (long) (bch * SATOSHIS_PER_BCH);
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
