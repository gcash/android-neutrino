package cash.bchd.android_neutrino.wallet;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class TransactionListTest {

    private static TransactionData tx(String txid, long amount, long timestamp, int height) {
        return new TransactionData(txid, amount > 0, "", amount, "", "", timestamp, "", height);
    }

    private static List<String> txids(List<TransactionData> list) {
        List<String> txids = new ArrayList<>();
        for (TransactionData tx : list) {
            txids.add(tx.getTxid());
        }
        return txids;
    }

    @Test
    public void dropsTransactionsTheWalletNoLongerHas() {
        List<TransactionData> shown = Arrays.asList(tx("replaced", -5000, 300, 0), tx("kept", 1000, 100, 10));
        List<TransactionData> wallet = Collections.singletonList(tx("kept", 1000, 100, 10));
        assertEquals(Collections.singletonList("kept"), txids(TransactionList.rebuild(shown, wallet)));
        assertEquals(Collections.emptyList(), TransactionList.rebuild(shown, Collections.emptyList()));
    }

    @Test
    public void addsNewTransactionsNewestFirst() {
        List<TransactionData> shown = Collections.singletonList(tx("b", 2000, 200, 20));
        List<TransactionData> wallet = Arrays.asList(tx("a", 1000, 100, 10), tx("b", 2000, 200, 20),
                tx("c", -500, 300, 0));
        assertEquals(Arrays.asList("c", "b", "a"), txids(TransactionList.rebuild(shown, wallet)));
    }

    @Test
    public void listsTransactionsOfOneBlockNewestFirst() {
        // The wallet lists them in block order: a payment, then its spending in the same block.
        List<TransactionData> wallet = Arrays.asList(tx("older", 1000, 100, 10), tx("received", 5000, 200, 20),
                tx("spent", -5000, 200, 20));
        assertEquals(Arrays.asList("spent", "received", "older"), txids(TransactionList.rebuild(Collections.emptyList(), wallet)));
    }

    @Test
    public void keepsWhatTheShownEntryHadAndTakesTheWalletsHeight() {
        TransactionData sent = new TransactionData("sent", false, "lunch", -5000, "$1.39", "USD", 300,
                "bitcoincash:qrhea03074073ff3zv9whh0nggxc7k03ssh8jv9mkx", 0);
        TransactionData mined = new TransactionData("sent", false, "", -5000, "$9.99", "USD", 400,
                "bitcoincash:qchange", 812345);
        List<TransactionData> list = TransactionList.rebuild(Collections.singletonList(sent), Collections.singletonList(mined));
        assertSame(sent, list.get(0));
        assertEquals(812345, sent.getHeight());
        assertEquals("lunch", sent.getMemo());
        assertEquals("$1.39", sent.getFiatAmount());
        assertEquals("bitcoincash:qrhea03074073ff3zv9whh0nggxc7k03ssh8jv9mkx", sent.getToAddress());
        assertEquals(300, sent.getTimestamp());
    }

    @Test
    public void fillsAFiatValueTheShownEntryLacked() {
        TransactionData shown = tx("a", 1000, 100, 10);
        TransactionData wallet = new TransactionData("a", true, "", 1000, "$0.28", "USD", 100, "", 10);
        TransactionList.rebuild(Collections.singletonList(shown), Collections.singletonList(wallet));
        assertEquals("$0.28", shown.getFiatAmount());
        assertEquals("USD", shown.getFiatCurrency());
    }

    @Test
    public void skipsTransactionsThatMoveNoCoins() {
        List<TransactionData> wallet = Arrays.asList(tx("zero", 0, 100, 10), tx("a", 1000, 200, 20));
        assertEquals(Collections.singletonList("a"), txids(TransactionList.rebuild(Collections.emptyList(), wallet)));
    }
}
