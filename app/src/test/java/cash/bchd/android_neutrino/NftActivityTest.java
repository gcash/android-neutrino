package cash.bchd.android_neutrino;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NftActivityTest {

    @Test
    public void putsAnAttributeOnOneLine() {
        assertEquals("Blue sky", NftActivity.oneLine(" Blue\n\r\tsky \n"));
        assertEquals("Red", NftActivity.oneLine("Red"));
    }

    @Test
    public void asksForTypesWhenItHasNoneOrAFetchIsOver() {
        assertTrue(NftActivity.asksForTypes(false, false));
        assertTrue(NftActivity.asksForTypes(true, true));
        assertFalse(NftActivity.asksForTypes(false, true));
    }
}
