package cash.bchd.android_neutrino.wallet;

import walletrpc.Api;

/**
 * SyncStatus is how far the wallet is from following the network, from what bchwallet's Network
 * call reports: whether it has peers, whether its chain of headers and filters caught up with
 * theirs, and whether the wallet scanned the blocks of that chain.
 */
public final class SyncStatus {

    public enum State {
        // No peer is connected, so nothing new reaches the wallet.
        WAITING_FOR_PEERS,
        // The chain backend fetches the headers and filters of blocks up to the peers' best.
        SYNCING,
        // The wallet scans the blocks of the chain for its transactions, as after a restore.
        UPDATING_WALLET,
        SYNCED,
    }

    public final State state;

    // The block height reached and the one to reach while SYNCING or UPDATING_WALLET.
    public final int height;
    public final int target;

    private SyncStatus(State state, int height, int target) {
        this.state = state;
        this.height = height;
        this.target = target;
    }

    public static SyncStatus of(Api.NetworkResponse network) {
        return of(network.getPeerCount(), network.getBestHeight(), network.getPeerBestHeight(),
                network.getChainCurrent(), network.getSyncedTo());
    }

    static SyncStatus of(int peerCount, int bestHeight, int peerBestHeight, boolean chainCurrent,
                         int syncedTo) {
        if (peerCount == 0) {
            return new SyncStatus(State.WAITING_FOR_PEERS, 0, 0);
        }
        if (!chainCurrent) {
            return new SyncStatus(State.SYNCING, bestHeight, Math.max(bestHeight, peerBestHeight));
        }
        // A block behind is the wallet still processing the newest block, not catching up.
        if (syncedTo < bestHeight - 1) {
            return new SyncStatus(State.UPDATING_WALLET, syncedTo, bestHeight);
        }
        return new SyncStatus(State.SYNCED, bestHeight, bestHeight);
    }
}
