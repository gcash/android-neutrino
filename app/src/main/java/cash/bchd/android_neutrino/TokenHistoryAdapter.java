package cash.bchd.android_neutrino;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import cash.bchd.android_neutrino.wallet.TokenChange;
import cash.bchd.android_neutrino.wallet.TransactionData;

/**
 * TokenHistoryAdapter lists, on a token's screen, the transactions that changed what the wallet
 * holds of the token, newest first, in the rows of the transaction list: whether the wallet
 * received or sent it, when, how much of it, and the transaction's confirmations.
 */
final class TokenHistoryAdapter extends RecyclerView.Adapter<TransactionAdapter.TxViewHolder> {

    interface OnClick {
        void onClick(TransactionData tx);
    }

    // A row: a transaction, how it changed the token, and the confirmations the row shows.
    private static final class Row {
        final TransactionData tx;
        final TokenChange change;
        final int confirmations;

        Row(TransactionData tx, TokenChange change, int confirmations) {
            this.tx = tx;
            this.change = change;
            this.confirmations = confirmations;
        }

        boolean sameAs(Row other) {
            return change.equals(other.change) && confirmations == other.confirmations
                    && tx.getTimestamp() == other.tx.getTimestamp();
        }
    }

    private final Context ctx;
    private final String category;
    private final OnClick onClick;
    private List<TransactionData> transactions = Collections.emptyList();
    private List<Row> rows = Collections.emptyList();
    private int blockHeight;

    TokenHistoryAdapter(Context ctx, String category, OnClick onClick) {
        this.ctx = ctx;
        this.category = category;
        this.onClick = onClick;
    }

    /**
     * setTransactions shows those of the transactions that changed the token, with their
     * confirmations at the block height, redrawing only the rows that changed.
     */
    void setTransactions(List<TransactionData> txs, int blockHeight) {
        this.transactions = txs;
        this.blockHeight = blockHeight;
        show();
    }

    void setBlockHeight(int blockHeight) {
        this.blockHeight = blockHeight;
        show();
    }

    private void show() {
        List<Row> shown = rows;
        List<Row> next = new ArrayList<>();
        for (TransactionData tx : transactions) {
            TokenChange change = TokenChange.find(tx.getTokens(), category);
            if (change != null) {
                next.add(new Row(tx, change, TransactionRows.confirmations(tx, blockHeight)));
            }
        }
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return shown.size();
            }

            @Override
            public int getNewListSize() {
                return next.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPosition, int newPosition) {
                return Objects.equals(shown.get(oldPosition).tx.getTxid(), next.get(newPosition).tx.getTxid());
            }

            @Override
            public boolean areContentsTheSame(int oldPosition, int newPosition) {
                return shown.get(oldPosition).sameAs(next.get(newPosition));
            }
        });
        rows = next;
        diff.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public TransactionAdapter.TxViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new TransactionAdapter.TxViewHolder((LinearLayout) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.tx_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionAdapter.TxViewHolder holder, int position) {
        Row row = rows.get(position);
        boolean received = row.change.received();
        holder.arrowImage.setImageResource(received ? R.drawable.receive_arrow : R.drawable.send_arrow);
        holder.txDescription.setText(row.change.swapped() ? R.string.swapped_nft
                : received ? R.string.token_received : R.string.token_sent);
        // In the phone's language and its 12 or 24 hour clock, with the year only if it isn't this
        // one.
        holder.txMemo.setText(DateUtils.formatDateTime(ctx, row.tx.getTimestamp() * 1000,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_MONTH));
        holder.bchAmount.setText(TokenTexts.changeOnTokenScreen(ctx, row.change));
        holder.bchAmount.setTextColor(ContextCompat.getColor(ctx, received ? R.color.darkGreen : R.color.neonPurple));

        // The status shows a dot before it until the transaction has ten confirmations.
        if (row.confirmations <= 0) {
            holder.fiatAmount.setText(R.string.unconfirmed);
            holder.fiatAmount.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.red_circle, 0, 0, 0);
        } else if (row.confirmations < 10) {
            holder.fiatAmount.setText(ctx.getResources().getQuantityString(R.plurals.confirmations,
                    row.confirmations, row.confirmations));
            holder.fiatAmount.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.yellow_circle, 0, 0, 0);
        } else {
            holder.fiatAmount.setText(null);
            holder.fiatAmount.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        }
        holder.layout.setOnClickListener(v -> onClick.onClick(row.tx));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }
}
