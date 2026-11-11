package cash.bchd.android_neutrino;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import cash.bchd.android_neutrino.wallet.TokenChange;
import cash.bchd.android_neutrino.wallet.TransactionData;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class TokenHistoryAdapterTest {

    private static TransactionData tx(String txid, TokenChange... changes) {
        TransactionData tx = new TransactionData(txid, true, "", 1000, "$0.01", "usd", 1600000000L, "", 1);
        tx.setTokens(Arrays.asList(changes));
        return tx;
    }

    @Test
    public void findsTheTransactionsThatChangedTheToken() {
        TokenChange mine = new TokenChange("aa", 5, 0);
        TokenChange other = new TokenChange("bb", 7, 0);
        List<TransactionData> txs = Arrays.asList(
                tx("c", mine, other), tx("b", other), tx("a", new TokenChange("aa", -2, 0)));

        List<TokenHistoryAdapter.Change> found = TokenHistoryAdapter.changes(txs, "aa");

        List<String> txids = new ArrayList<>();
        for (TokenHistoryAdapter.Change change : found) {
            txids.add(change.tx.getTxid());
        }
        assertEquals(Arrays.asList("c", "a"), txids);
        assertSame(mine, found.get(0).change);
        assertEquals(-2, found.get(1).change.amount);
        assertEquals(Collections.emptyList(), TokenHistoryAdapter.changes(txs, "cc"));
    }
}
