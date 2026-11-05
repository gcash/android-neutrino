package cash.bchd.android_neutrino.tokens;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TokenIconsTest {

    @Test
    public void imagesPastTheLimitAreRefused() {
        assertTrue(TokenIcons.usable(512, 512));
        assertTrue(TokenIcons.usable(4096, 4096));
        assertFalse(TokenIcons.usable(4097, 10));
        assertFalse(TokenIcons.usable(10, 100000));
        assertFalse(TokenIcons.usable(0, 10));
        assertFalse(TokenIcons.usable(-1, -1));
    }

    @Test
    public void imagesScaleDownWithoutGoingUnderTheSize() {
        assertEquals(1, TokenIcons.sampleSize(64, 64, 64));
        assertEquals(1, TokenIcons.sampleSize(100, 100, 64));
        assertEquals(2, TokenIcons.sampleSize(128, 128, 64));
        assertEquals(8, TokenIcons.sampleSize(1024, 512, 64));
    }
}
