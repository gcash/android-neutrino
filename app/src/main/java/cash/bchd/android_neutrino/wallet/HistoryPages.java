package cash.bchd.android_neutrino.wallet;

/**
 * HistoryPages splits a read of all the wallet's transactions into ranges of blocks, as one reply
 * for a long history can be larger than a reply may be, which failed every read: a range whose
 * reply is too large is read again in half the blocks, and one whose reply is small has the next
 * read in twice as many. The last range runs past the tip, so that its reply also has the
 * unmined transactions. It isn't safe for use by several threads at once.
 */
final class HistoryPages {

    // How many blocks the first range has, and how many any range has at most.
    static final int FIRST_BLOCKS = 20_000;
    static final int MOST_BLOCKS = 320_000;
    // A reply smaller than this has the next range twice as long.
    static final long GROW_BELOW_BYTES = 4 << 20;

    /**
     * Page is a range of blocks to read, from start through end, or through the tip and the
     * unmined transactions if open.
     */
    static final class Page {
        final int start;
        final int end;
        final boolean open;

        Page(int start, int end, boolean open) {
            this.start = start;
            this.end = end;
            this.open = open;
        }
    }

    private final int tip;
    private int start = 1;
    private int blocks = FIRST_BLOCKS;
    private boolean done;

    /**
     * HistoryPages reads the history up to the tip, as the wallet's chain has it when the read
     * begins. Blocks that come during the read are in the last range, which runs past it.
     */
    HistoryPages(int tip) {
        this.tip = tip;
    }

    /**
     * done returns whether the last range was read.
     */
    boolean done() {
        return done;
    }

    /**
     * next returns the range to read now.
     */
    Page next() {
        long end = (long) start + blocks - 1;
        if (end >= tip) {
            return new Page(start, 0, true);
        }
        return new Page(start, (int) end, false);
    }

    /**
     * smaller has the range read again in half the blocks, as its reply was too large, and
     * returns false if it was one block already, which can't be split.
     */
    boolean smaller() {
        if (blocks == 1) {
            return false;
        }
        blocks = Math.max(1, blocks / 2);
        return true;
    }

    /**
     * read takes the range as read, with the size of its reply, and moves on past it.
     */
    void read(Page page, long replyBytes) {
        if (page.open) {
            done = true;
            return;
        }
        start = page.end + 1;
        if (replyBytes < GROW_BELOW_BYTES) {
            blocks = Math.min(MOST_BLOCKS, blocks * 2);
        }
    }
}
