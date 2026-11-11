package cash.bchd.android_neutrino.wallet;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChainTipTest {

    @Test
    public void movesOnlyWhenTheBestBlockChanges() {
        ChainTip tip = new ChainTip();
        assertTrue(tip.move(100, "a"));
        // Recovery's notifications of old blocks leave the chain's best block as it was.
        assertFalse(tip.move(100, "a"));
        assertTrue(tip.move(101, "b"));
        // A reorg to a block of the same height, or a lower one.
        assertTrue(tip.move(101, "c"));
        assertTrue(tip.move(100, "d"));
        assertFalse(tip.move(100, "d"));
    }
}
