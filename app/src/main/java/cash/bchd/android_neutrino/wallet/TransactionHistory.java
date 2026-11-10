package cash.bchd.android_neutrino.wallet;

import com.google.protobuf.ByteString;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import walletrpc.Api;

/**
 * TransactionHistory is the wallet's transactions as the app last read them all, kept up to date
 * from the wallet's notifications, so that a new block's transactions don't have the app read
 * them all again, which for a wallet with a long history is a large reply each time. It keeps
 * them oldest first, as the wallet lists them, which decides between transactions of the same
 * time. It isn't safe for use by several threads at once.
 */
final class TransactionHistory {

    /**
     * Entries makes the list's entry for one of the wallet's transactions.
     */
    interface Entries {
        TransactionData of(Api.TransactionDetails tx, int height, long timestamp);
    }

    private final LinkedHashMap<String, TransactionData> transactions = new LinkedHashMap<>();

    /**
     * replace takes all of the wallet's transactions, as a read of them all gives them.
     */
    void replace(List<TransactionData> all) {
        transactions.clear();
        for (TransactionData tx : all) {
            transactions.put(tx.getTxid(), tx);
        }
    }

    /**
     * apply brings the transactions up to date with a notification, and returns whether they
     * changed: those of the blocks it attached, mined, after the others; those it saw unmined;
     * and no unmined ones but those it says are unmined now, as one a double spend replaced is
     * gone. A notification that detached blocks needs a read of them all instead.
     */
    boolean apply(Api.TransactionNotificationsResponse value, Entries entries) {
        boolean changed = false;
        for (Api.BlockDetails block : value.getAttachedBlocksList()) {
            for (Api.TransactionDetails tx : block.getTransactionsList()) {
                TransactionData entry = entries.of(tx, block.getHeight(), block.getTimestamp());
                // Removed first, so that one that was unmined moves to its block's place.
                transactions.remove(entry.getTxid());
                transactions.put(entry.getTxid(), entry);
                changed = true;
            }
        }
        for (Api.TransactionDetails tx : value.getUnminedTransactionsList()) {
            // An unmined transaction has the time the wallet received it.
            TransactionData entry = entries.of(tx, 0, tx.getTimestamp());
            TransactionData had = transactions.get(entry.getTxid());
            if (had != null && had.getHeight() > 0) {
                continue;
            }
            transactions.put(entry.getTxid(), entry);
            changed = true;
        }
        Set<String> unmined = new HashSet<>();
        for (ByteString hash : value.getUnminedTransactionHashesList()) {
            unmined.add(Wallet.txidToString(hash));
        }
        Iterator<TransactionData> all = transactions.values().iterator();
        while (all.hasNext()) {
            TransactionData tx = all.next();
            if (tx.getHeight() == 0 && !unmined.contains(tx.getTxid())) {
                all.remove();
                changed = true;
            }
        }
        return changed;
    }

    /**
     * copies returns the transactions, oldest first, each a copy, as what they're handed to sets
     * their fiat amounts.
     */
    List<TransactionData> copies() {
        List<TransactionData> copies = new ArrayList<>(transactions.size());
        for (TransactionData tx : transactions.values()) {
            copies.add(tx.copy());
        }
        return copies;
    }
}
