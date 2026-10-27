package cash.bchd.android_neutrino.wallet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bip39 checks a recovery phrase the way bchwallet does before it creates a wallet from it: 12,
 * 15, 18, 21 or 24 words of the BIP39 English word list, whose last bits are a checksum of the
 * others. Checking first catches a mistyped phrase before the app deletes the current wallet.
 */
public final class Bip39 {

    public enum Problem {
        NONE,
        WORD_COUNT,
        UNKNOWN_WORDS,
        CHECKSUM,
    }

    /**
     * Check is what is wrong with a phrase, and the words of it that aren't in the word list.
     */
    public static final class Check {
        public final Problem problem;
        public final List<String> unknownWords;

        private Check(Problem problem, List<String> unknownWords) {
            this.problem = problem;
            this.unknownWords = Collections.unmodifiableList(unknownWords);
        }

        private Check(Problem problem) {
            this(problem, Collections.emptyList());
        }
    }

    private static Map<String, Integer> wordIndex;

    private Bip39() {
    }

    /**
     * normalize returns the phrase in lower case with single spaces between its words.
     */
    public static String normalize(String phrase) {
        return phrase.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /**
     * check returns what is wrong with the phrase, after normalizing it.
     */
    public static Check check(String phrase) {
        String normalized = normalize(phrase);
        String[] words = normalized.isEmpty() ? new String[0] : normalized.split(" ");
        if (words.length < 12 || words.length > 24 || words.length % 3 != 0) {
            return new Check(Problem.WORD_COUNT);
        }

        Map<String, Integer> index = wordIndex();
        List<String> unknown = new ArrayList<>();
        for (String word : words) {
            if (!index.containsKey(word) && !unknown.contains(word)) {
                unknown.add(word);
            }
        }
        if (!unknown.isEmpty()) {
            return new Check(Problem.UNKNOWN_WORDS, unknown);
        }

        // Each word is 11 bits: the entropy, then as many bits of its SHA-256 hash as the
        // entropy has 32 bit words.
        int bits = words.length * 11;
        int checksumBits = bits / 33;
        int entropyBits = bits - checksumBits;
        boolean[] phraseBits = new boolean[bits];
        for (int i = 0; i < words.length; i++) {
            int value = index.get(words[i]);
            for (int j = 0; j < 11; j++) {
                phraseBits[i * 11 + j] = (value >> (10 - j) & 1) == 1;
            }
        }
        byte[] entropy = new byte[entropyBits / 8];
        for (int i = 0; i < entropyBits; i++) {
            if (phraseBits[i]) {
                entropy[i / 8] |= (byte) (1 << (7 - i % 8));
            }
        }
        byte[] hash = sha256(entropy);
        for (int i = 0; i < checksumBits; i++) {
            boolean expected = (hash[i / 8] >> (7 - i % 8) & 1) == 1;
            if (phraseBits[entropyBits + i] != expected) {
                return new Check(Problem.CHECKSUM);
            }
        }
        return new Check(Problem.NONE);
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static synchronized Map<String, Integer> wordIndex() {
        if (wordIndex == null) {
            InputStream in = Bip39.class.getResourceAsStream("bip39_english.txt");
            if (in == null) {
                throw new IllegalStateException("The BIP39 word list is missing");
            }
            Map<String, Integer> index = new HashMap<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isEmpty()) {
                        index.put(line, index.size());
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("Can't read the BIP39 word list", e);
            }
            if (index.size() != 2048) {
                throw new IllegalStateException("The BIP39 word list has " + index.size() + " words");
            }
            wordIndex = index;
        }
        return wordIndex;
    }
}
