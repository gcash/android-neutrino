package cash.bchd.android_neutrino;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class BackupThreeTest {

    @Test
    public void quizzesAnyWordOfALongPhrase() {
        Random random = new Random(1);
        boolean pastTwelve = false;
        for (int i = 0; i < 200; i++) {
            int[] ns = BackupThree.quizIndexes(24, random);
            assertEquals(3, ns.length);
            assertNotEquals(ns[0], ns[1]);
            assertNotEquals(ns[1], ns[2]);
            assertNotEquals(ns[0], ns[2]);
            for (int n : ns) {
                assertTrue(n >= 0 && n < 24);
                pastTwelve |= n >= 12;
            }
        }
        assertTrue("never quizzed a word past the twelfth", pastTwelve);
    }

    @Test
    public void acceptsWordsAsAKeyboardTypesThem() {
        assertTrue(BackupThree.matches("Abandon ", "abandon"));
        assertTrue(BackupThree.matches(" zoo", "zoo"));
        assertFalse(BackupThree.matches("abandons", "abandon"));
        assertFalse(BackupThree.matches("", "abandon"));
    }
}
