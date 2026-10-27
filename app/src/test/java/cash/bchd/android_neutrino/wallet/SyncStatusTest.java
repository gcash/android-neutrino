package cash.bchd.android_neutrino.wallet;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SyncStatusTest {

    private static void assertStatus(SyncStatus.State state, int height, int target, SyncStatus status) {
        assertEquals(state, status.state);
        assertEquals(height, status.height);
        assertEquals(target, status.target);
    }

    @Test
    public void waitsForPeersWithoutAny() {
        assertEquals(SyncStatus.State.WAITING_FOR_PEERS, SyncStatus.of(0, 0, 0, false, 0).state);
        // A wallet that was synced gets nothing new without peers either.
        assertEquals(SyncStatus.State.WAITING_FOR_PEERS, SyncStatus.of(0, 974663, 0, true, 974663).state);
    }

    @Test
    public void syncsUpToThePeersBestBlock() {
        assertStatus(SyncStatus.State.SYNCING, 812345, 974663, SyncStatus.of(1, 812345, 974663, false, 0));
        // At the peers' height, the backend can still be fetching filters.
        assertStatus(SyncStatus.State.SYNCING, 974663, 974663, SyncStatus.of(1, 974663, 974600, false, 0));
    }

    @Test
    public void updatesTheWalletOnceTheChainIsCurrent() {
        assertStatus(SyncStatus.State.UPDATING_WALLET, 575000, 974663, SyncStatus.of(2, 974663, 974663, true, 575000));
    }

    @Test
    public void isSyncedWithinABlockOfTheChain() {
        assertEquals(SyncStatus.State.SYNCED, SyncStatus.of(1, 974663, 974663, true, 974663).state);
        assertEquals(SyncStatus.State.SYNCED, SyncStatus.of(1, 974664, 974664, true, 974663).state);
    }
}
