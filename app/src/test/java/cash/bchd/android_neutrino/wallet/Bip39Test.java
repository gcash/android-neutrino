package cash.bchd.android_neutrino.wallet;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class Bip39Test {

    private static String repeat(String word, int times) {
        return String.join(" ", Collections.nCopies(times, word));
    }

    // Phrases of the BIP39 test vectors, and ones that go-bip39, which bchwallet checks phrases
    // with, made of 20 and 28 bytes of entropy.
    private static final String[] VALID = {
            repeat("abandon", 11) + " about",
            repeat("abandon", 14) + " address",
            repeat("abandon", 17) + " agent",
            repeat("abandon", 20) + " admit",
            repeat("abandon", 23) + " art",
            "legal winner thank year wave sausage worth useful legal winner thank yellow",
            "letter advice cage absurd amount doctor acoustic avoid letter advice cage above",
            "letter advice cage absurd amount doctor acoustic avoid letter advice cage absurd amount doctor acoustic avoid letter always",
            repeat("zoo", 11) + " wrong",
            repeat("zoo", 14) + " wrist",
            repeat("zoo", 20) + " veteran",
            repeat("zoo", 23) + " vote",
    };

    @Test
    public void acceptsValidPhrasesOfEveryLength() {
        for (String phrase : VALID) {
            assertEquals(phrase, Bip39.Problem.NONE, Bip39.check(phrase).problem);
        }
    }

    @Test
    public void normalizesCaseAndSpaces() {
        String phrase = "  Abandon " + repeat("ABANDON", 10) + "\n about \t";
        assertEquals(repeat("abandon", 11) + " about", Bip39.normalize(phrase));
        assertEquals(Bip39.Problem.NONE, Bip39.check(phrase).problem);
    }

    @Test
    public void refusesOtherWordCounts() {
        for (int count : new int[]{0, 11, 13, 14, 25, 27}) {
            assertEquals(count + " words", Bip39.Problem.WORD_COUNT, Bip39.check(repeat("abandon", count)).problem);
        }
    }

    @Test
    public void namesWordsOutsideTheList() {
        Bip39.Check check = Bip39.check(repeat("abandon", 9) + " bitcoinz abandon bitcoinz");
        assertEquals(Bip39.Problem.UNKNOWN_WORDS, check.problem);
        assertEquals(Collections.singletonList("bitcoinz"), check.unknownWords);
        assertEquals(Arrays.asList("abandn", "abot"), Bip39.check(repeat("abandon", 10) + " abandn abot").unknownWords);
    }

    @Test
    public void refusesABadChecksum() {
        assertEquals(Bip39.Problem.CHECKSUM, Bip39.check(repeat("abandon", 12)).problem);
        assertEquals(Bip39.Problem.CHECKSUM, Bip39.check(repeat("zoo", 12)).problem);
        assertEquals(Bip39.Problem.CHECKSUM, Bip39.check(repeat("abandon", 23) + " about").problem);
        // Two words swapped.
        assertEquals(Bip39.Problem.CHECKSUM, Bip39.check("winner legal thank year wave sausage worth useful legal winner thank yellow").problem);
    }
}
