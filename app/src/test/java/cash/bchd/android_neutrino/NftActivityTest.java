package cash.bchd.android_neutrino;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NftActivityTest {

    @Test
    public void putsAnAttributeOnOneLine() {
        assertEquals("Blue sky", NftActivity.oneLine(" Blue\n\r\tsky \n"));
        assertEquals("Red", NftActivity.oneLine("Red"));
    }
}
