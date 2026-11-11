package cash.bchd.android_neutrino.wallet;

import java.util.ArrayList;
import java.util.List;

import walletrpc.Api;

/**
 * PendingNotifications keeps the notifications that come while all of the wallet's transactions
 * are read, to bring what the read gives up to date once it's done, rather than read them all
 * again for each, as recovery sends one for each block it finds the wallet's addresses in, and a
 * read of a long history takes long enough that reads ran back to back. Applying one the read
 * already covered changes nothing, as each replaces entries by transaction, and the unmined
 * transactions are those the newest says. It isn't safe for use by several threads at once.
 */
final class PendingNotifications {

    // How many it keeps at most, past which a read of them all costs less.
    static final int MOST = 500;

    private final List<Api.TransactionNotificationsResponse> kept = new ArrayList<>();

    /**
     * add keeps the notification, and returns false if it can't: one of a reorg, which moved
     * the transactions of the blocks it detached, or one too many, so that a read of them all
     * after the one under way has to cover it, and what it kept so far, which it forgets.
     */
    boolean add(Api.TransactionNotificationsResponse notification) {
        if (notification.getDetachedBlocksCount() > 0 || kept.size() >= MOST) {
            kept.clear();
            return false;
        }
        kept.add(notification);
        return true;
    }

    /**
     * applyTo applies what it kept to the history, in the order they came, forgets them, and
     * returns whether the history changed.
     */
    boolean applyTo(TransactionHistory history, TransactionHistory.Entries entries) {
        boolean changed = false;
        for (Api.TransactionNotificationsResponse notification : kept) {
            changed |= history.apply(notification, entries);
        }
        kept.clear();
        return changed;
    }

    /**
     * clear forgets what it kept, as for a wallet that stopped.
     */
    void clear() {
        kept.clear();
    }

    boolean isEmpty() {
        return kept.isEmpty();
    }
}
