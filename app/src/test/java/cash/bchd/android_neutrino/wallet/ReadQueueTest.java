package cash.bchd.android_neutrino.wallet;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReadQueueTest {

    @Test
    public void readsOneAtATimeAndOnceMoreForAllWhoAskedMeanwhile() {
        ReadQueue<String> reads = new ReadQueue<>();
        assertTrue(reads.start("main"));
        assertTrue(reads.reading());

        // Notifications come many at once, from two screens' listeners.
        assertFalse(reads.start("main"));
        assertFalse(reads.start("token"));
        assertFalse(reads.start("main"));

        assertEquals(new LinkedHashSet<>(Arrays.asList("main", "token")), reads.done());
        assertTrue(reads.reading());

        // None asked while that one was read.
        assertEquals(Collections.emptySet(), reads.done());
        assertFalse(reads.reading());
        assertTrue(reads.start("main"));
    }

    @Test
    public void stopsForAWalletThatStopped() {
        ReadQueue<String> reads = new ReadQueue<>();
        reads.start("main");
        reads.start("token");
        reads.stop();
        assertFalse(reads.reading());
        assertTrue(reads.start("main"));
        assertEquals(Collections.emptySet(), reads.done());
    }
}
