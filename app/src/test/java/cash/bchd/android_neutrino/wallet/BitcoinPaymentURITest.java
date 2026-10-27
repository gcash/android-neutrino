package cash.bchd.android_neutrino.wallet;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BitcoinPaymentURITest {

    private static final String ADDRESS = "qzce52668kd8tmhw5dx9ks6aayfgtjg645ftt3k3cy";

    @Test
    public void writesSmallAmountsInPlainNotation() {
        BitcoinPaymentURI uri = new BitcoinPaymentURI.Builder().address(ADDRESS).amount(0.0001).build();
        assertEquals("bitcoincash:" + ADDRESS + "?amount=0.0001", uri.getURI());
    }

    @Test
    public void writesAmountsWithoutTrailingZeros() {
        BitcoinPaymentURI uri = new BitcoinPaymentURI.Builder().address(ADDRESS).amount(1.5).build();
        assertEquals("bitcoincash:" + ADDRESS + "?amount=1.5", uri.getURI());
    }
}
