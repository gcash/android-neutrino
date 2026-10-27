package cash.bchd.android_neutrino.wallet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TransactionList makes the list of transactions the main screen shows from the ones the wallet
 * has, so that the list loses a transaction the wallet no longer has, such as one a double spend
 * replaced, and stays ordered.
 */
public final class TransactionList {

    private TransactionList() {
    }

    /**
     * rebuild returns the wallet's transactions that move coins, newest first. The wallet lists
     * them oldest first, in block order, which decides between transactions of the same time, such
     * as a payment and its spending in one block. A transaction that was shown already keeps its
     * entry, with the fiat value of when it was first shown and the memo of a payment the app sent,
     * which the wallet doesn't have, and takes the wallet's height and recipients for it, which
     * corrects the recipient of an entry saved before the wallet named recipients.
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
            if (tx.getAmount() == 0) {
                continue;
            }
            TransactionData old = previous.remove(tx.getTxid());
            if (old == null) {
                list.add(tx);
                continue;
            }
            old.setHeight(tx.getHeight());
            old.setRecipients(tx.getToAddress(), tx.getMoreRecipients(), tx.getToYourself());
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
}
