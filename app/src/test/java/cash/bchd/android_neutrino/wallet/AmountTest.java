package cash.bchd.android_neutrino.wallet;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AmountTest {

    // The amounts that truncating a double made one satoshi short, the first
    // from gcash/android-neutrino#24.
    private static final String[] AMOUNTS = {"0.03346", "0.29", "0.57", "1.15"};
    private static final long[] SATOSHIS = {3346000, 29000000, 57000000, 115000000};

    @Test
    public void doubleAmountsRoundToTheNearestSatoshi() {
        for (int i = 0; i < AMOUNTS.length; i++) {
            assertEquals(AMOUNTS[i], SATOSHIS[i], new Amount(Double.parseDouble(AMOUNTS[i])).getSatoshis());
        }
    }

    @Test
    public void parsesTypedAmountsExactly() {
        for (int i = 0; i < AMOUNTS.length; i++) {
            assertEquals(AMOUNTS[i], SATOSHIS[i], Amount.fromBCH(AMOUNTS[i]).getSatoshis());
        }
        assertEquals(3346000, Amount.fromBCH("0,03346").getSatoshis());
        assertEquals(1, Amount.fromBCH(" 0.00000001 ").getSatoshis());
        assertEquals(2100000000000000L, Amount.fromBCH("21000000").getSatoshis());
        assertEquals(1, Amount.fromBCH("0.000000005").getSatoshis());
    }

    @Test(expected = NumberFormatException.class)
    public void rejectsText() {
        Amount.fromBCH("one BCH");
    }
}
