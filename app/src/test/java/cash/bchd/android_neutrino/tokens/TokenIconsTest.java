package cash.bchd.android_neutrino.tokens;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

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
    public void tellsWhatCanAnimateByItsStart() {
        assertTrue(TokenIcons.canAnimate("GIF89a".getBytes()));
        assertTrue(TokenIcons.canAnimate("GIF87a".getBytes()));
        assertTrue(TokenIcons.canAnimate(webp("VP8X", 0x02)));
        assertTrue(TokenIcons.canAnimate(webp("VP8X", 0x12)));
        assertFalse(TokenIcons.canAnimate(webp("VP8X", 0x10)));
        assertFalse(TokenIcons.canAnimate(webp("VP8 ", 0x02)));
        assertFalse(TokenIcons.canAnimate(Arrays.copyOf(webp("VP8X", 0x02), 20)));
        byte[] notRiff = webp("VP8X", 0x02);
        notRiff[0] = 'X';
        assertFalse(TokenIcons.canAnimate(notRiff));
        assertFalse(TokenIcons.canAnimate("\u0089PNG".getBytes()));
        assertFalse(TokenIcons.canAnimate(new byte[0]));
    }

    // webp returns the start of a WebP file with the chunk and the flags.
    private static byte[] webp(String chunk, int flags) {
        byte[] start = new byte[30];
        byte[] names = ("RIFF\0\0\0\0WEBP" + chunk).getBytes();
        System.arraycopy(names, 0, start, 0, names.length);
        start[20] = (byte) flags;
        return start;
    }

    @Test
    public void fitsAnImageInItsProportions() {
        assertEquals("[100, 50]", java.util.Arrays.toString(TokenIcons.fitted(400, 200, 100)));
        assertEquals("[25, 100]", java.util.Arrays.toString(TokenIcons.fitted(100, 400, 100)));
        assertEquals("[100, 100]", java.util.Arrays.toString(TokenIcons.fitted(300, 300, 100)));
        assertEquals("[1, 100]", java.util.Arrays.toString(TokenIcons.fitted(1, 4000, 100)));
    }
}
