package cash.bchd.android_neutrino.wallet;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void findsRequiredParameters() {
        // BIP 21 has a link refused that requires a parameter the wallet doesn't understand.
        BitcoinPaymentURI plain = BitcoinPaymentURI.parse("bitcoincash:" + ADDRESS + "?amount=1&label=Shop&extra=1");
        assertNotNull(plain);
        assertNull(plain.getRequiredParameter());

        BitcoinPaymentURI required = BitcoinPaymentURI.parse("bitcoincash:" + ADDRESS + "?amount=1&req-zeta=1&req-alpha=2");
        assertNotNull(required);
        assertEquals("alpha", required.getRequiredParameter());
    }

    @Test
    public void findsTokenRequests() {
        // As Cashonize and Selene write a request for fungible tokens of a category.
        String category = "8473d94f604de351cdee3030f6c354d36b257861ad8e95bbc0a06fbab2a2f9cf";
        BitcoinPaymentURI tokens = BitcoinPaymentURI.parse("bitcoincash:" + ADDRESS + "?c=" + category + "&f=100");
        assertNotNull(tokens);
        assertTrue(tokens.requestsTokens());

        BitcoinPaymentURI bch = BitcoinPaymentURI.parse("bitcoincash:" + ADDRESS + "?amount=1");
        assertNotNull(bch);
        assertFalse(bch.requestsTokens());
    }
}
