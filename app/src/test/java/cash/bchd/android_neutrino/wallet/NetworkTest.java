package cash.bchd.android_neutrino.wallet;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NetworkTest {

    @Test
    public void findsTheNetworkOfAPaymentURI() {
        assertEquals(Network.MAINNET, Network.ofUri("bitcoincash:qrhea03074073ff3zv9whh0nggxc7k03ssh8jv9mkx?amount=1"));
        assertEquals(Network.TESTNET4, Network.ofUri("bchtest:qqfx3wcg8ts09mt5l3zey06wenapyfqq2qrcyj5x0s"));
        // QR codes often write URIs in capitals.
        assertEquals(Network.TESTNET4, Network.ofUri("BCHTEST:QQFX3WCG8TS09MT5L3ZEY06WENAPYFQQ2QRCYJ5X0S"));
        assertEquals(Network.MAINNET, Network.ofUri(" bitcoincash:?r=https://example.com/i/1"));
    }

    @Test
    public void findsNoNetworkForAnythingElse() {
        assertNull(Network.ofUri("qrhea03074073ff3zv9whh0nggxc7k03ssh8jv9mkx"));
        assertNull(Network.ofUri("bitcoin:1BoatSLRHtKNngkdXEeobR76b53LETtpyT"));
        assertNull(Network.ofUri(""));
        assertNull(Network.ofUri(null));
    }

    @Test
    public void readsTheSavedNetwork() {
        assertEquals(Network.TESTNET4, Network.fromId("testnet4"));
        assertEquals(Network.MAINNET, Network.fromId("mainnet"));
        // An unknown network, such as one a later version saved, runs mainnet's wallet.
        assertEquals(Network.MAINNET, Network.fromId("chipnet"));
        assertEquals(Network.MAINNET, Network.fromId(null));
    }

    @Test
    public void hasFiatOnMainnetOnly() {
        assertTrue(Network.MAINNET.hasFiat);
        assertFalse(Network.TESTNET4.hasFiat);
        assertEquals("bchtest:", Network.TESTNET4.uriPrefix());
    }
}
