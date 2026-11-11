package cash.bchd.android_neutrino.wallet;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

public class TellEachTest {

    @Test
    public void tellsTheOthersWhenOneThrows() {
        List<String> told = new ArrayList<>();
        List<List<TransactionData>> lists = new ArrayList<>();
        WalletEventListener throwing = new WalletEventListener() {
            @Override
            public void onGetTransactions(List<TransactionData> txs, int blockHeight) {
                told.add("throwing");
                throw new NullPointerException("a saved entry without a fiat amount");
            }
        };
        WalletEventListener other = new WalletEventListener() {
            @Override
            public void onGetTransactions(List<TransactionData> txs, int blockHeight) {
                told.add("other");
                lists.add(txs);
            }
        };
        Map<WalletEventListener, Long> replies = new LinkedHashMap<>();
        replies.put(throwing, throwing.transactionReplies.next());
        replies.put(other, other.transactionReplies.next());
        List<TransactionData> read = Collections.singletonList(
                new TransactionData("a", true, "", 1000, "$0.01", "usd", 1600000000L, "", 1));

        Wallet.tellEach(replies, read, 100, () -> false);

        assertEquals(List.of("throwing", "other"), told);
        // The second has its own copies.
        assertNotSame(read, lists.get(0));
    }

    @Test
    public void tellsNoneOnceTheWalletStopped() {
        List<String> told = new ArrayList<>();
        WalletEventListener listener = new WalletEventListener() {
            @Override
            public void onGetTransactions(List<TransactionData> txs, int blockHeight) {
                told.add("told");
            }
        };
        Map<WalletEventListener, Long> replies = Collections.singletonMap(listener, listener.transactionReplies.next());

        Wallet.tellEach(replies, Collections.emptyList(), 100, () -> true);

        assertEquals(Collections.emptyList(), told);
    }
}
