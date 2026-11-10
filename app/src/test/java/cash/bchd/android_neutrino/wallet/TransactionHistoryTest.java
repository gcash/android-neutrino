package cash.bchd.android_neutrino.wallet;

import com.google.protobuf.ByteString;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import walletrpc.Api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TransactionHistoryTest {

    // hash returns a transaction hash whose bytes are all b.
    private static ByteString hash(int b) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) b);
        return ByteString.copyFrom(bytes);
    }

    // received returns a transaction that pays the wallet 1000 satoshis.
    private static Api.TransactionDetails received(int b) {
        return Api.TransactionDetails.newBuilder()
                .setHash(hash(b))
                .setTimestamp(1761500000L + b)
                .addCredits(Api.TransactionDetails.Output.newBuilder().setAmount(1000))
                .build();
    }

    private static Api.BlockDetails block(int height, Api.TransactionDetails... txs) {
        return Api.BlockDetails.newBuilder()
                .setHeight(height)
                .setTimestamp(1761600000L + height)
                .addAllTransactions(Arrays.asList(txs))
                .build();
    }

    // notification returns a notification of the blocks and unmined transactions, with the
    // hashes of all that are unmined now.
    private static Api.TransactionNotificationsResponse notification(List<Api.BlockDetails> blocks,
                                                                     List<Api.TransactionDetails> unmined,
                                                                     int... unminedNow) {
        Api.TransactionNotificationsResponse.Builder n = Api.TransactionNotificationsResponse.newBuilder()
                .addAllAttachedBlocks(blocks)
                .addAllUnminedTransactions(unmined);
        for (int b : unminedNow) {
            n.addUnminedTransactionHashes(hash(b));
        }
        return n.build();
    }

    private static final TransactionHistory.Entries ENTRIES =
            (tx, height, timestamp) -> Wallet.transactionData(tx, height, timestamp, "");

    private static String txid(int b) {
        return Wallet.txidToString(hash(b));
    }

    // txids returns the transactions' IDs and heights, oldest first.
    private static List<String> txids(TransactionHistory history) {
        List<String> ids = new ArrayList<>();
        for (TransactionData tx : history.copies()) {
            ids.add(tx.getTxid() + "@" + tx.getHeight());
        }
        return ids;
    }

    private static TransactionHistory readOf(Api.TransactionDetails... mined) {
        TransactionHistory history = new TransactionHistory();
        List<TransactionData> all = new ArrayList<>();
        for (int i = 0; i < mined.length; i++) {
            all.add(ENTRIES.of(mined[i], 100 + i, 1761600100L + i));
        }
        history.replace(all);
        return history;
    }

    @Test
    public void addsANewBlocksTransactionsAfterTheOthers() {
        TransactionHistory history = readOf(received(1), received(2));
        assertTrue(history.apply(notification(Arrays.asList(block(200, received(3), received(4))),
                new ArrayList<>()), ENTRIES));
        assertEquals(Arrays.asList(txid(1) + "@100", txid(2) + "@101", txid(3) + "@200", txid(4) + "@200"),
                txids(history));
    }

    @Test
    public void movesAnUnminedOneToItsBlockOnceMined() {
        TransactionHistory history = readOf(received(1));
        assertTrue(history.apply(notification(new ArrayList<>(), Arrays.asList(received(5)), 5), ENTRIES));
        assertTrue(history.apply(notification(new ArrayList<>(), Arrays.asList(received(6)), 5, 6), ENTRIES));
        assertEquals(Arrays.asList(txid(1) + "@100", txid(5) + "@0", txid(6) + "@0"), txids(history));

        assertTrue(history.apply(notification(Arrays.asList(block(201, received(5))), new ArrayList<>(), 6), ENTRIES));
        assertEquals(Arrays.asList(txid(1) + "@100", txid(6) + "@0", txid(5) + "@201"), txids(history));
    }

    @Test
    public void dropsAnUnminedOneTheWalletNoLongerHas() {
        TransactionHistory history = readOf(received(1));
        history.apply(notification(new ArrayList<>(), Arrays.asList(received(5)), 5), ENTRIES);
        // A block that mined a double spend of it, which isn't the wallet's.
        assertTrue(history.apply(notification(Arrays.asList(block(201)), new ArrayList<>()), ENTRIES));
        assertEquals(Arrays.asList(txid(1) + "@100"), txids(history));
    }

    @Test
    public void keepsAMinedOneThatsToldUnminedLate() {
        TransactionHistory history = readOf(received(1));
        assertFalse(history.apply(notification(new ArrayList<>(), Arrays.asList(received(1)), 1), ENTRIES));
        assertEquals(Arrays.asList(txid(1) + "@100"), txids(history));
    }

    @Test
    public void saysWhenNothingChanged() {
        TransactionHistory history = readOf(received(1));
        assertFalse(history.apply(notification(Arrays.asList(block(201)), new ArrayList<>()), ENTRIES));
    }

    @Test
    public void handsOnCopies() {
        TransactionHistory history = readOf(received(1));
        history.copies().get(0).setFiatAmount("$1.00");
        assertEquals("", history.copies().get(0).getFiatAmount());
    }
}
