package cash.bchd.android_neutrino.tokens;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NftAdapterTest {

    @Test
    public void shortensOnlyLongCommitments() {
        assertEquals("01", NftAdapter.shortCommitment("01"));
        assertEquals("0011223344556677", NftAdapter.shortCommitment("0011223344556677"));
        assertEquals("001122\u20267788", NftAdapter.shortCommitment("001122334455667788"));
    }

    @Test
    public void ordersNamesByTheirNumbers() {
        assertTrue(NftAdapter.compareNatural("Puffer #192", "Puffer #1144") < 0);
        assertTrue(NftAdapter.compareNatural("Puffer #1144", "Puffer #192") > 0);
        assertEquals(0, NftAdapter.compareNatural("Puffer #7", "Puffer #7"));
        assertTrue(NftAdapter.compareNatural("Ninja #2 - B", "Ninja #10 - A") < 0);
        assertTrue(NftAdapter.compareNatural("Apple", "banana") < 0);
        assertTrue(NftAdapter.compareNatural("Reaper", "Reaper #1") < 0);
    }
}
