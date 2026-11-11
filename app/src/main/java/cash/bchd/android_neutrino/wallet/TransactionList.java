package cash.bchd.android_neutrino.wallet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TransactionList makes the list of transactions the main screen shows from the ones the wallet
 * has, so that the list loses a transaction the wallet no longer has, such as one a double spend
 * replaced, and stays ordered.
 */
public final class TransactionList {

    private TransactionList() {
    }

    /**
     * rebuild returns the wallet's transactions that move coins or tokens, newest first. The
     * wallet lists them oldest first, in block order, which decides between transactions of the
     * same time, such as a payment and its spending in one block. A transaction that was shown
     * already keeps its entry, with the fiat value of when it was first shown and the memo of a
     * payment the app sent, which the wallet doesn't have, and takes the wallet's amount, height,
     * recipients, tokens and change of the spendable balance for it, which corrects the recipient
     * of an entry saved before the wallet named recipients, and gives tokens to one saved before
     * the app read them.
     */
    public static List<TransactionData> rebuild(List<TransactionData> shown, List<TransactionData> wallet) {
        Map<String, TransactionData> previous = new HashMap<>();
        for (TransactionData tx : shown) {
            previous.put(tx.getTxid(), tx);
        }
        List<TransactionData> list = new ArrayList<>();
        List<TransactionData> newestFirst = new ArrayList<>(wallet);
        Collections.reverse(newestFirst);
        for (TransactionData tx : newestFirst) {
            if (tx.getAmount() == 0 && tx.getTokens().isEmpty()) {
                continue;
            }
            TransactionData old = previous.remove(tx.getTxid());
            if (old == null) {
                list.add(tx);
                continue;
            }
            // The wallet's amount, as an entry the app kept of a payment it sent has its own.
            old.setAmount(tx.getAmount());
            old.setHeight(tx.getHeight());
            old.setRecipients(tx.getToAddress(), tx.getMoreRecipients(), tx.getToYourself());
            old.setTokens(tx.getTokens());
            old.setSpendableAmount(tx.getSpendableAmount());
            if (old.getFiatAmount().isEmpty()) {
                old.setFiatAmount(tx.getFiatAmount());
                old.setFiatCurrency(tx.getFiatCurrency());
            }
            list.add(old);
        }
        // A stable sort, which keeps transactions of the same time newest first.
        Collections.sort(list, Collections.reverseOrder());
        return list;
    }

    /**
     * withoutHidden returns the transactions but those that moved only tokens the user hid, and
     * moved the spendable balance by less than the floor either way, as spam tokens come. A
     * transaction that also moved BCH, or another token, stays.
     */
    public static List<TransactionData> withoutHidden(List<TransactionData> txs, Set<String> hidden) {
        List<TransactionData> list = new ArrayList<>();
        for (TransactionData tx : txs) {
            if (!movedOnlyHidden(tx, hidden) || Math.abs(tx.getSpendableAmount()) >= TokenBalance.FLOOR) {
                list.add(tx);
            }
        }
        return list;
    }

    /**
     * tokens returns the tokens that the transactions moved.
     */
    public static Set<String> tokens(List<TransactionData> txs) {
        Set<String> tokens = new HashSet<>();
        for (TransactionData tx : txs) {
            for (TokenChange change : tx.getTokens()) {
                tokens.add(change.category);
            }
        }
        return tokens;
    }

    private static boolean movedOnlyHidden(TransactionData tx, Set<String> hidden) {
        if (tx.getTokens().isEmpty()) {
            return false;
        }
        for (TokenChange change : tx.getTokens()) {
            if (!hidden.contains(change.category)) {
                return false;
            }
        }
        return true;
    }
}
