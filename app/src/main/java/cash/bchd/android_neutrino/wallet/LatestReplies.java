package cash.bchd.android_neutrino.wallet;

/**
 * LatestReplies numbers requests as they're made, and hands on their replies in that order,
 * dropping a reply that a later request's reply overtook.
 */
final class LatestReplies {
    private long made;
    private long delivered;

    /**
     * next returns the number of a request about to be made.
     */
    synchronized long next() {
        return ++made;
    }

    /**
     * deliver runs what hands on the reply to a request, unless a later one's was handed on. It
     * holds the lock meanwhile, so that what's done with replies stays in their order.
     */
    synchronized void deliver(long reply, Runnable handOn) {
        if (reply > delivered) {
            delivered = reply;
            handOn.run();
        }
    }
}
