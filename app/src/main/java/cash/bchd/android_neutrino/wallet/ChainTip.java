package cash.bchd.android_neutrino.wallet;

import java.util.Objects;

/**
 * ChainTip keeps the best block of the wallet's chain that the listeners were told, so that they
 * are told again only when it moves. It's safe for use by several threads at once.
 */
final class ChainTip {

    private int height;
    private String hash;

    /**
     * move takes the chain's best block, and returns whether it isn't the one it had, which can be
     * lower, as after a reorg.
     */
    synchronized boolean move(int height, String hash) {
        if (height == this.height && Objects.equals(hash, this.hash)) {
            return false;
        }
        this.height = height;
        this.hash = hash;
        return true;
    }
}
