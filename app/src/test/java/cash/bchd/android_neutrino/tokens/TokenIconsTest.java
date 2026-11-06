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

    @Test
    public void tellsArtThatMovesByItsUri() {
        assertTrue(TokenIcons.movingUri("ipfs://cid/18.mp4"));
        assertTrue(TokenIcons.movingUri("ipfs://cid/18.GIF"));
        assertFalse(TokenIcons.movingUri("ipfs://cid/18.png"));
    }

    @Test
    public void tellsAGifByItsStart() {
        assertTrue(TokenIcons.isGif(new byte[]{'G', 'I', 'F', '8', '9'}));
        assertFalse(TokenIcons.isGif(new byte[]{'G', 'I'}));
        assertFalse(TokenIcons.isGif(new byte[]{1, 2, 3, 4}));
    }
}
