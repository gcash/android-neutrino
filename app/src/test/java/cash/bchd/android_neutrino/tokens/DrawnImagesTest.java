package cash.bchd.android_neutrino.tokens;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class DrawnImagesTest {

    @Test
    public void letsGoOfWhatWasUsedLongestAgo() {
        DrawnImages<String> drawn = new DrawnImages<>(10, String::length);
        drawn.put("a", "1234");
        drawn.put("b", "1234");
        assertEquals("1234", drawn.get("a"));
        drawn.put("c", "1234");
        assertNull(drawn.get("b"));
        assertEquals("1234", drawn.get("a"));
        assertEquals("1234", drawn.get("c"));
    }

    @Test
    public void countsAReplacedImageOnce() {
        DrawnImages<String> drawn = new DrawnImages<>(10, String::length);
        drawn.put("a", "12345");
        drawn.put("a", "12345");
        drawn.put("b", "12345");
        assertEquals("12345", drawn.get("a"));
        assertEquals("12345", drawn.get("b"));
    }

    @Test
    public void letsGoOfAToken() {
        DrawnImages<String> drawn = new DrawnImages<>(100, String::length);
        drawn.put("aa:icon:64", "x");
        drawn.put("aa:nft:64:01", "x");
        drawn.put("bb:icon:64", "x");
        drawn.removeStartingWith("aa");
        assertNull(drawn.get("aa:icon:64"));
        assertNull(drawn.get("aa:nft:64:01"));
        assertEquals("x", drawn.get("bb:icon:64"));
        drawn.put("cc", "x".repeat(99));
        assertEquals("x", drawn.get("bb:icon:64"));
    }
}
