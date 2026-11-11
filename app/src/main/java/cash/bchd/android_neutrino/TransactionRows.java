package cash.bchd.android_neutrino;

import android.widget.TextView;

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
                    tx.getFiatAmount(), tx.getSentTo(), tx.getToAddress(), tx.getMoreRecipients(), tx.getTokens(),
                    tx.getSpendableAmount());
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
     * showStatus shows a row's status, with a dot before it until the transaction has ten
     * confirmations: unconfirmed, then how many it has, and then what the row shows of it once
     * it's confirmed, which can be nothing.
     */
    static void showStatus(TextView status, int confirmations, CharSequence confirmed) {
        if (confirmations <= 0) {
            status.setText(R.string.unconfirmed);
            status.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.red_circle, 0, 0, 0);
        } else if (confirmations < 10) {
            status.setText(status.getResources().getQuantityString(R.plurals.confirmations, confirmations, confirmations));
            status.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.yellow_circle, 0, 0, 0);
        } else {
            status.setText(confirmed);
            status.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        }
    }

    /**
     * newTop returns whether the transaction at the top is another one than was shown.
     */
    static boolean newTop(List<TransactionData> shown, List<TransactionData> txs) {
        if (txs.isEmpty()) {
            return false;
        }
        return shown.isEmpty() || !shown.get(0).getTxid().equals(txs.get(0).getTxid());
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
