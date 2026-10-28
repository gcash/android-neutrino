package cash.bchd.android_neutrino;

import androidx.recyclerview.widget.DiffUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cash.bchd.android_neutrino.wallet.TransactionData;

/**
 * TransactionRows keeps what the rows of the transaction list show, to tell which rows changed
 * when the transactions or the block height do. The old list of transactions can't tell:
 * TransactionList.rebuild updates in place the entries it keeps.
 */
final class TransactionRows {

    private TransactionRows() {
    }

    /**
     * Row is what a row shows of a transaction.
     */
    static final class Row {
        final String txid;
        private final List<Object> contents;

        Row(TransactionData tx, int blockHeight) {
            txid = tx.getTxid();
            contents = Arrays.asList(tx.getAmount(), tx.getIncoming(), tx.getMemo(), confirmations(tx, blockHeight),
                    tx.getFiatAmount(), tx.getSentTo(), tx.getToAddress(), tx.getMoreRecipients());
        }
    }

    static List<Row> of(List<TransactionData> txs, int blockHeight) {
        List<Row> rows = new ArrayList<>();
        for (TransactionData tx : txs) {
            rows.add(new Row(tx, blockHeight));
        }
        return rows;
    }

    /**
     * confirmations returns the confirmations that a row shows for the transaction: 0 for one
     * that isn't mined, and 10 for 10 or more, which the row shows alike.
     */
    static int confirmations(TransactionData tx, int blockHeight) {
        if (tx.getHeight() <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(blockHeight - tx.getHeight() + 1, 10));
    }

    /**
     * diff returns how the rows changed from the ones shown, for the list to add, remove, move
     * and redraw only those.
     */
    static DiffUtil.DiffResult diff(List<Row> shown, List<Row> rows) {
        return DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return shown.size();
            }

            @Override
            public int getNewListSize() {
                return rows.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPosition, int newPosition) {
                return shown.get(oldPosition).txid.equals(rows.get(newPosition).txid);
            }

            @Override
            public boolean areContentsTheSame(int oldPosition, int newPosition) {
                return shown.get(oldPosition).contents.equals(rows.get(newPosition).contents);
            }
        });
    }
}
