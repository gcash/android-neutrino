package cash.bchd.android_neutrino.wallet;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HistoryPagesTest {

    // ranges reads the pages to the end, with replies of the bytes the function gives for each
    // range, where -1 is one too large, and returns the ranges read, as "start-end" or "start-".
    private static List<String> ranges(int tip, java.util.function.ToLongFunction<HistoryPages.Page> bytes) {
        HistoryPages pages = new HistoryPages(tip);
        List<String> read = new ArrayList<>();
        while (!pages.done()) {
            HistoryPages.Page page = pages.next();
            long size = bytes.applyAsLong(page);
            if (size < 0) {
                assertTrue("a one block range was too large", pages.smaller());
                continue;
            }
            read.add(page.start + "-" + (page.open ? "" : page.end));
            pages.read(page, size);
        }
        return read;
    }

    @Test
    public void readsAShortChainInOneOpenRange() {
        assertEquals(java.util.Collections.singletonList("1-"), ranges(15_000, page -> 100));
    }

    @Test
    public void growsRangesWhoseRepliesAreSmallUpToTheMost() {
        assertEquals(java.util.Arrays.asList("1-20000", "20001-60000", "60001-140000",
                "140001-300000", "300001-620000", "620001-"), ranges(900_000, page -> 100));
    }

    @Test
    public void halvesARangeWhoseReplyIsTooLarge() {
        // Blocks 100,000 and on are busy: a range with more than 5,000 of them is too large.
        List<String> read = ranges(200_000, page -> {
            int end = page.open ? 200_000 : page.end;
            int busy = Math.max(0, end - Math.max(page.start, 100_000) + 1);
            return busy > 5_000 ? -1 : HistoryPages.GROW_BELOW_BYTES;
        });
        int next = 1;
        for (String range : read) {
            String[] ends = range.split("-", -1);
            assertEquals("the ranges leave out no block", next, Integer.parseInt(ends[0]));
            if (!ends[1].isEmpty()) {
                next = Integer.parseInt(ends[1]) + 1;
            }
        }
        assertTrue(read.get(read.size() - 1).endsWith("-"));
    }

    @Test
    public void givesUpOnABlockTooLargeAlone() {
        HistoryPages pages = new HistoryPages(10);
        for (int i = 0; i < 20 && pages.smaller(); i++) {
        }
        assertFalse(pages.smaller());
    }
}
