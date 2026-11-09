package cash.bchd.android_neutrino;

/**
 * NetworkChanges tells when the device's default network is another than before, as when it
 * moves from cellular to Wi-Fi. The first one it's told of is no change.
 */
final class NetworkChanges {

    private String last;

    /**
     * changed takes the default network, and returns whether it's another than the one before.
     */
    synchronized boolean changed(String network) {
        boolean changed = last != null && !last.equals(network);
        last = network;
        return changed;
    }
}
