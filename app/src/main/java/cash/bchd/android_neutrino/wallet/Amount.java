package cash.bchd.android_neutrino.wallet;

public class Amount {

    private static final int SATOSHIS_PER_BCH = 100000000;
    private long satoshis;

    public Amount(long satoshis) {
        this.satoshis = satoshis;
    }

    public double toBCH() {
        Long sats = new Long(this.satoshis);
        return sats.doubleValue() / SATOSHIS_PER_BCH;
    }

    public String toString() {
        Long sats = new Long(this.satoshis);
        return removeTrailingZeros(sats.doubleValue() / SATOSHIS_PER_BCH);
    }

    private static String removeTrailingZeros(double d) {
        return String.format("%.12f", d).replaceAll("[0]*$", "").replaceAll(".$", "");
    }
}
