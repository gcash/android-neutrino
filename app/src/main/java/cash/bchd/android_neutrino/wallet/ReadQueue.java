package cash.bchd.android_neutrino.wallet;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * ReadQueue has at most one read of all the wallet's transactions under way, and at most one
 * more, for all who asked while it was, which notifications that come many at once would
 * otherwise each start. It isn't safe for use by several threads at once.
 */
final class ReadQueue<L> {

    private boolean reading;
    private final Set<L> again = new LinkedHashSet<>();

    /**
     * start returns whether the listener's read is to start now, or else keeps it for the read
     * after the one under way.
     */
    boolean start(L listener) {
        if (reading) {
            again.add(listener);
            return false;
        }
        reading = true;
        return true;
    }

    /**
     * done ends the read under way, and returns the listeners of the read to make next, which is
     * then under way, or none.
     */
    Set<L> done() {
        if (again.isEmpty()) {
            reading = false;
            return Collections.emptySet();
        }
        Set<L> next = new LinkedHashSet<>(again);
        again.clear();
        return next;
    }

    /**
     * takeQueued returns the listeners who asked while the read was under way, and keeps the
     * reading under way, as for a read that failed and is made again later for them too.
     */
    Set<L> takeQueued() {
        Set<L> queued = new LinkedHashSet<>(again);
        again.clear();
        return queued;
    }

    /**
     * stop ends the reads, as for a wallet that stopped.
     */
    void stop() {
        again.clear();
        reading = false;
    }

    boolean reading() {
        return reading;
    }
}
