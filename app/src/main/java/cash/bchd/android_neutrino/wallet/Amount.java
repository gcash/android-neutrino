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
        return String.valueOf(sats.doubleValue() / SATOSHIS_PER_BCH);
    }
}
