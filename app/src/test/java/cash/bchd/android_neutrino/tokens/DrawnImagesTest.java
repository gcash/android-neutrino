package cash.bchd.android_neutrino.tokens;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void aDrawingWhoseScopeWentIsLetGo() {
        DrawnImages<String> drawn = new DrawnImages<>(100, String::length);
        String[] nft = {"aa", "aa:nft:01:"};
        long[] seen = drawn.generations(nft);
        drawn.removeStartingWith("aa:nft:02:");
        assertTrue(drawn.putIfCurrent("aa:nft:01:64", "x", nft, seen));
        seen = drawn.generations(nft);
        drawn.removeStartingWith("aa:nft:01:");
        assertFalse(drawn.putIfCurrent("aa:nft:01:64", "y", nft, seen));
        assertNull(drawn.get("aa:nft:01:64"));
        seen = drawn.generations(nft);
        drawn.removeStartingWith("aa");
        assertFalse(drawn.putIfCurrent("aa:nft:01:64", "y", nft, seen));
        seen = drawn.generations(nft);
        drawn.clear();
        assertFalse(drawn.putIfCurrent("aa:nft:01:64", "y", nft, seen));
        seen = drawn.generations(nft);
        drawn.removeStartingWith("bb");
        assertTrue(drawn.putIfCurrent("aa:nft:01:64", "z", nft, seen));
        assertEquals("z", drawn.get("aa:nft:01:64"));
    }
}
