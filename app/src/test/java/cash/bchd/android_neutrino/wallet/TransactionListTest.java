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
    public void keepsWhatTheShownEntryHadAndTakesTheWalletsHeightAndRecipients() {
        // Saved by an earlier version, which named the wallet's change address as the recipient.
        TransactionData sent = new TransactionData("sent", false, "lunch", -5000, "$1.39", "USD", 300,
                "qr95sy3j9xwd2ap32xkykttr4cvcu7as4y0qverfuy", 0);
        TransactionData mined = new TransactionData("sent", false, "", -5000, "$9.99", "USD", 400, "", 812345);
        mined.setRecipients("qrhea03074073ff3zv9whh0nggxc7k03ssh8jv9mkx", 2, false);
        List<TransactionData> list = TransactionList.rebuild(Collections.singletonList(sent), Collections.singletonList(mined));
        assertSame(sent, list.get(0));
        assertEquals(812345, sent.getHeight());
        assertEquals("lunch", sent.getMemo());
        assertEquals("$1.39", sent.getFiatAmount());
        assertEquals(300, sent.getTimestamp());
        assertEquals("qrhea03074073ff3zv9whh0nggxc7k03ssh8jv9mkx", sent.getToAddress());
        assertEquals(2, sent.getMoreRecipients());
    }

    @Test
    public void learnsThatAShownSendPaidTheWallet() {
        TransactionData shown = new TransactionData("self", false, "", -200, "", "", 100,
                "qr95sy3j9xwd2ap32xkykttr4cvcu7as4y0qverfuy", 10);
        TransactionData wallet = tx("self", -200, 100, 10);
        wallet.setRecipients("", 0, true);
        TransactionList.rebuild(Collections.singletonList(shown), Collections.singletonList(wallet));
        assertEquals(TransactionData.SentTo.YOURSELF, shown.getSentTo());
    }

    @Test
    public void givesTheWalletsTokensToAnEntrySavedBeforeTheAppReadThem() {
        TransactionData shown = tx("a", 1000, 100, 10);
        TransactionData wallet = tx("a", 1000, 100, 10);
        wallet.setTokens(Collections.singletonList(new TokenChange("bb", 25, 0)));
        wallet.setSpendableAmount(-300);
        TransactionList.rebuild(Collections.singletonList(shown), Collections.singletonList(wallet));
        assertEquals(Collections.singletonList(new TokenChange("bb", 25, 0)), shown.getTokens());
        assertEquals(-300, shown.getSpendableAmount());
    }

    @Test
    public void keepsTransactionsThatMoveTokensAndNoCoins() {
        // Tokens that came in a swap for as much BCH as their output carries, so that the
        // transaction moved no coins.
        TransactionData tokensOnly = tx("tokens", 0, 100, 10);
        tokensOnly.setTokens(Collections.singletonList(new TokenChange("bb", 25, 0)));
        List<TransactionData> wallet = Arrays.asList(tokensOnly, tx("zero", 0, 200, 20));
        assertEquals(Collections.singletonList("tokens"), txids(TransactionList.rebuild(Collections.emptyList(), wallet)));
    }

    @Test
    public void leavesOutTheTransactionsOfHiddenTokens() {
        // A spam token's payment, a payment of a hidden token with BCH, one of a hidden token
        // and another, and a payment of BCH.
        TransactionData spam = tx("spam", 1000, 400, 10);
        spam.setTokens(Collections.singletonList(new TokenChange("aa", 1000000, 0)));
        TransactionData withBch = tx("withBch", 101000, 300, 10);
        withBch.setTokens(Collections.singletonList(new TokenChange("aa", 5, 0)));
        withBch.setSpendableAmount(100000);
        TransactionData both = tx("both", 2000, 200, 10);
        both.setTokens(Arrays.asList(new TokenChange("aa", 5, 0), new TokenChange("bb", 7, 0)));
        TransactionData bch = tx("bch", 5000, 100, 10);
        bch.setSpendableAmount(5000);
        List<TransactionData> txs = Arrays.asList(spam, withBch, both, bch);
        assertEquals(Arrays.asList("withBch", "both", "bch"),
                txids(TransactionList.withoutHidden(txs, Collections.singleton("aa"))));
        assertEquals(txs, TransactionList.withoutHidden(txs, Collections.emptySet()));
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

    @Test
    public void collectsTheTokensMoved() {
        TransactionData one = tx("01", 0, 1, 1);
        one.setTokens(Arrays.asList(new TokenChange("aa", 5, 0), new TokenChange("bb", 7, 0)));
        TransactionData two = tx("02", 0, 2, 2);
        two.setTokens(Collections.singletonList(new TokenChange("aa", 1, 0)));
        TransactionData plain = tx("03", 1000, 3, 3);
        assertEquals(new java.util.HashSet<>(Arrays.asList("aa", "bb")),
                TransactionList.tokens(Arrays.asList(one, two, plain)));
    }

    @Test
    public void keepsTheMemoOfASentPaymentAndTakesTheWalletsAmount() {
        // As the app kept it when it sent it: the amount sent and the fee, and the memo.
        TransactionData kept = tx("sent", -10_250, 300, 0);
        kept.setMemo("rent");
        // As the wallet tells of it, without the memo.
        TransactionData told = tx("sent", -10_226, 299, 0);
        List<TransactionData> list = TransactionList.rebuild(Collections.singletonList(kept),
                Collections.singletonList(told));
        assertEquals(1, list.size());
        assertEquals("rent", list.get(0).getMemo());
        assertEquals(-10_226, list.get(0).getAmount());
    }

    @Test
    public void tellsASavedEntryItWasAFusion() {
        // Saved before the app told fusions.
        TransactionData saved = tx("fused", -1200, 300, 10);
        TransactionData told = tx("fused", -1200, 300, 10);
        told.setFusion(new TransactionData.Fusion(42, 38, 2, 3));
        List<TransactionData> list = TransactionList.rebuild(Collections.singletonList(saved),
                Collections.singletonList(told));
        assertEquals(new TransactionData.Fusion(42, 38, 2, 3), list.get(0).getFusion());
    }
}
