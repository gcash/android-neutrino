package cash.bchd.android_neutrino.wallet;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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

    @Test
    public void readsURIsInCapitals() {
        // As QR codes often write them, also in Turkish, where a capital I lowercases to a dotless i.
        String address = ADDRESS.toUpperCase(Locale.ROOT);
        for (String language : new String[]{"en-US", "tr-TR"}) {
            InLocale.run(language, () -> {
                BitcoinPaymentURI uri = BitcoinPaymentURI.parse("BITCOINCASH:" + address + "?amount=0.5");
                assertNotNull(language, uri);
                assertEquals(language, address, uri.getAddress());
                assertEquals(language, 0.5, uri.getAmount(), 0);
            });
        }
    }
}
