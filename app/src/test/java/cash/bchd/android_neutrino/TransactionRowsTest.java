package cash.bchd.android_neutrino;

import androidx.recyclerview.widget.ListUpdateCallback;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import cash.bchd.android_neutrino.wallet.TransactionData;

import static org.junit.Assert.assertEquals;

public class TransactionRowsTest {

    private static TransactionData tx(String txid, int height) {
        return new TransactionData(txid, true, "", 1000, "$0.01", "usd", 1600000000L, "", height);
    }

    // The changes that a diff dispatches to the list.
    private static List<String> changes(List<TransactionRows.Row> shown, List<TransactionRows.Row> rows) {
        List<String> changes = new ArrayList<>();
        TransactionRows.diff(shown, rows).dispatchUpdatesTo(new ListUpdateCallback() {
            @Override
            public void onInserted(int position, int count) {
                changes.add("insert " + position + " " + count);
            }

            @Override
            public void onRemoved(int position, int count) {
                changes.add("remove " + position + " " + count);
            }

            @Override
            public void onMoved(int fromPosition, int toPosition) {
                changes.add("move " + fromPosition + " " + toPosition);
            }

            @Override
            public void onChanged(int position, int count, Object payload) {
                changes.add("change " + position + " " + count);
            }
        });
        return changes;
    }

    @Test
    public void redrawsATransactionUpdatedInPlace() {
        // TransactionList.rebuild sets the height of the entry it keeps, as when it's mined.
        TransactionData a = tx("a", 0);
        List<TransactionRows.Row> shown = TransactionRows.of(Collections.singletonList(a), 100);
        a.setHeight(100);
        assertEquals(Collections.singletonList("change 0 1"),
                changes(shown, TransactionRows.of(Collections.singletonList(a), 100)));
    }

    @Test
    public void insertsANewTransactionAtTheTop() {
        TransactionData a = tx("a", 90);
        List<TransactionRows.Row> shown = TransactionRows.of(Collections.singletonList(a), 100);
        assertEquals(Collections.singletonList("insert 0 1"),
                changes(shown, TransactionRows.of(Arrays.asList(tx("b", 0), a), 100)));
    }

    @Test
    public void removesATransactionTheWalletDropped() {
        TransactionData a = tx("a", 0);
        TransactionData b = tx("b", 90);
        List<TransactionRows.Row> shown = TransactionRows.of(Arrays.asList(a, b), 100);
        assertEquals(Collections.singletonList("remove 0 1"),
                changes(shown, TransactionRows.of(Collections.singletonList(b), 100)));
    }

    @Test
    public void redrawsOnlyRowsWhoseConfirmationsShowDifferently() {
        // At the next block, 6 confirmations become 7, while 11 and an unmined transaction show
        // the same.
        List<TransactionData> txs = Arrays.asList(tx("unmined", 0), tx("recent", 95), tx("old", 90));
        assertEquals(Collections.singletonList("change 1 1"),
                changes(TransactionRows.of(txs, 100), TransactionRows.of(txs, 101)));
    }

    @Test
    public void countsConfirmationsAsTheRowShowsThem() {
        assertEquals(0, TransactionRows.confirmations(tx("a", 0), 100));
        assertEquals(1, TransactionRows.confirmations(tx("a", 100), 100));
        assertEquals(9, TransactionRows.confirmations(tx("a", 92), 100));
        assertEquals(10, TransactionRows.confirmations(tx("a", 91), 100));
        assertEquals(10, TransactionRows.confirmations(tx("a", 1), 100));
        // A height above the tip's, as during a reorg, shows as unconfirmed.
        assertEquals(0, TransactionRows.confirmations(tx("a", 101), 100));
    }
}
