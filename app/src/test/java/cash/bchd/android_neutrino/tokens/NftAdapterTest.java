package cash.bchd.android_neutrino.tokens;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NftAdapterTest {

    @Test
    public void shortensOnlyLongCommitments() {
        assertEquals("01", NftAdapter.shortCommitment("01"));
        assertEquals("0011223344556677", NftAdapter.shortCommitment("0011223344556677"));
        assertEquals("001122\u20267788", NftAdapter.shortCommitment("001122334455667788"));
    }
}
