package cash.bchd.android_neutrino.wallet;

import com.google.protobuf.ByteString;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import walletrpc.Api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PendingNotificationsTest {

    private static ByteString hash(int b) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) b);
        return ByteString.copyFrom(bytes);
    }

    private static Api.TransactionDetails received(int b) {
        return Api.TransactionDetails.newBuilder()
                .setHash(hash(b))
                .setTimestamp(1761500000L + b)
                .addCredits(Api.TransactionDetails.Output.newBuilder().setAmount(1000))
                .build();
    }

    private static Api.TransactionNotificationsResponse mined(int height, int... txs) {
        Api.BlockDetails.Builder block = Api.BlockDetails.newBuilder()
                .setHeight(height).setTimestamp(1761600000L + height);
        for (int b : txs) {
            block.addTransactions(received(b));
        }
        return Api.TransactionNotificationsResponse.newBuilder().addAttachedBlocks(block).build();
    }

    private static Api.TransactionNotificationsResponse unmined(int tx, int... unminedNow) {
        Api.TransactionNotificationsResponse.Builder n = Api.TransactionNotificationsResponse.newBuilder()
                .addUnminedTransactions(received(tx));
        for (int b : unminedNow) {
            n.addUnminedTransactionHashes(hash(b));
        }
        return n.build();
    }

    private static final TransactionHistory.Entries ENTRIES =
            (tx, height, timestamp) -> Wallet.transactionData(tx, height, timestamp, "");

    private static List<String> txids(TransactionHistory history) {
        List<String> ids = new ArrayList<>();
        for (TransactionData tx : history.copies()) {
            ids.add(tx.getTxid() + "@" + tx.getHeight());
        }
        return ids;
    }

    // read returns a history as a read of them all gives it, of the mined transactions at the
    // heights, transaction b at height 100 + b.
    private static TransactionHistory read(int... mined) {
        TransactionHistory history = new TransactionHistory();
        List<TransactionData> all = new ArrayList<>();
        for (int b : mined) {
            all.add(ENTRIES.of(received(b), 100 + b, 1761600100L + b));
        }
        history.replace(all);
        return history;
    }

    @Test
    public void bringsAReadUpToDateAsApplyingThemDirectlyWould() {
        List<Api.TransactionNotificationsResponse> came = Arrays.asList(
                mined(101, 1), unmined(5, 5), mined(206, 6), mined(207, 5));

        // Applied as they came, to the history before them.
        TransactionHistory direct = read();
        for (Api.TransactionNotificationsResponse n : came) {
            direct.apply(n, ENTRIES);
        }

        // Kept while a read went on that already had the first one's transaction.
        PendingNotifications pending = new PendingNotifications();
        for (Api.TransactionNotificationsResponse n : came) {
            assertTrue(pending.add(n));
        }
        TransactionHistory afterRead = read(1);
        assertTrue(pending.applyTo(afterRead, ENTRIES));
        assertTrue(pending.isEmpty());

        assertEquals(txids(direct), txids(afterRead));
    }

    @Test
    public void leavesAReorgToAReadOfThemAll() {
        PendingNotifications pending = new PendingNotifications();
        assertTrue(pending.add(mined(101, 1)));
        Api.TransactionNotificationsResponse reorg = Api.TransactionNotificationsResponse.newBuilder()
                .addDetachedBlocks(hash(9)).build();
        assertFalse(pending.add(reorg));
        assertTrue("what it kept is forgotten, as the read after covers it", pending.isEmpty());
    }

    @Test
    public void keepsNoMoreThanItCostsToReadThemAll() {
        PendingNotifications pending = new PendingNotifications();
        for (int i = 0; i < PendingNotifications.MOST; i++) {
            assertTrue(pending.add(mined(200 + i)));
        }
        assertFalse(pending.add(mined(1000)));
        assertTrue(pending.isEmpty());
        assertFalse(pending.applyTo(read(), ENTRIES));
        assertEquals(Collections.emptyList(), txids(read()));
    }
}
