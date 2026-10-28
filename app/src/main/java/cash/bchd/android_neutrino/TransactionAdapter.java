package cash.bchd.android_neutrino;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import cash.bchd.android_neutrino.wallet.Amount;
import cash.bchd.android_neutrino.wallet.Network;
import cash.bchd.android_neutrino.wallet.TransactionData;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TxViewHolder> {

    private List<TransactionData> mDataset;
    private Context ctx;
    private CoordinatorLayout cLayout;
    private int blockHeight;
    // The network of the transactions.
    private final Network network;
    // What the rows showed when the transactions or the block height were last set.
    private List<TransactionRows.Row> shownRows;

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    public static class TxViewHolder extends RecyclerView.ViewHolder {
        TextView bchAmount;
        TextView fiatAmount;
        TextView txDescription;
        TextView txMemo;
        ImageView arrowImage;
        ImageView confirmationCircle;
        LinearLayout layout;
        public TxViewHolder(LinearLayout v) {
            super(v);
            layout = v;
            bchAmount = v.findViewById(R.id.bchAmount);
            fiatAmount = v.findViewById(R.id.fiatAmount);
            txDescription = v.findViewById(R.id.txDescription);
            txMemo = v.findViewById(R.id.txMemo);
            arrowImage = v.findViewById(R.id.arrowImage);
            confirmationCircle = v.findViewById(R.id.confirmationCircle);
        }
    }

    // Provide a suitable constructor (depends on the kind of dataset)
    public TransactionAdapter(List<TransactionData> myDataset, Context context, CoordinatorLayout layout, int height, Network network) {
        mDataset = myDataset;
        ctx = context;
        cLayout = layout;
        blockHeight = height;
        this.network = network;
        shownRows = TransactionRows.of(myDataset, height);
    }

    /**
     * setNewData shows the transactions, adding, removing, moving and redrawing only the rows
     * that changed, which the list animates.
     */
    public void setNewData(List<TransactionData> data) {
        List<TransactionRows.Row> rows = TransactionRows.of(data, blockHeight);
        DiffUtil.DiffResult diff = TransactionRows.diff(shownRows, rows);
        mDataset = data;
        shownRows = rows;
        diff.dispatchUpdatesTo(this);
    }

    public List<TransactionData> getData() {
        return mDataset;
    }

    /**
     * setBlockHeight shows the transactions' confirmations at the height, redrawing only the
     * rows that show them differently.
     */
    public void setBlockHeight(int height) {
        blockHeight = height;
        List<TransactionRows.Row> rows = TransactionRows.of(mDataset, height);
        DiffUtil.DiffResult diff = TransactionRows.diff(shownRows, rows);
        shownRows = rows;
        diff.dispatchUpdatesTo(this);
    }

    // Create new views (invoked by the layout manager)
    @Override
    public TransactionAdapter.TxViewHolder onCreateViewHolder(ViewGroup parent,
                                                              int viewType) {
        // create a new view
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.tx_row, parent, false);
        // set the view's size, margins, paddings and layout parameters
        TxViewHolder vh = new TxViewHolder((LinearLayout)v);
        return vh;
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(TxViewHolder holder, int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element

        TransactionData tx = mDataset.get(position);
        String bch = "₿" + new Amount(tx.getAmount()).toString();
        holder.bchAmount.setText(bch);

        int confirmations = TransactionRows.confirmations(tx, blockHeight);
        if (confirmations <= 0) {
            holder.fiatAmount.setText(R.string.unconfirmed);
            holder.confirmationCircle.setImageResource(R.drawable.red_circle);
            holder.confirmationCircle.setVisibility(View.VISIBLE);
        } else if (confirmations < 10) {
            holder.confirmationCircle.setImageResource(R.drawable.yellow_circle);
            holder.confirmationCircle.setVisibility(View.VISIBLE);
            holder.fiatAmount.setText(ctx.getResources().getQuantityString(R.plurals.confirmations, confirmations, confirmations));
        } else {
            holder.confirmationCircle.setVisibility(View.INVISIBLE);
            holder.fiatAmount.setText(tx.getFiatAmount());
        }

        holder.txMemo.setEllipsize(TextUtils.TruncateAt.END);
        if (tx.getIncoming()) {
            holder.txDescription.setText(R.string.received_bitcoin_cash);
            holder.arrowImage.setImageResource(R.drawable.receive_arrow);
            holder.bchAmount.setTextColor(ctx.getResources().getColor(R.color.darkGreen));
            if (tx.getMemo().equals("")) {
                holder.txMemo.setText(R.string.from_bitcoin_cash_address);
            } else {
                holder.txMemo.setText(tx.getMemo());
            }
        } else {
            holder.arrowImage.setImageResource(R.drawable.send_arrow);
            holder.txDescription.setText(R.string.sent_bitcoin_cash);
            holder.bchAmount.setTextColor(ctx.getResources().getColor(R.color.neonPurple));
            if (tx.getMemo() == null || tx.getMemo().equals("")) {
                holder.txMemo.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                holder.txMemo.setText(sentTo(tx, holder.txMemo));
            } else {
                holder.txMemo.setText(tx.getMemo());
            }
        }

        holder.layout.setOnClickListener(new LinearLayout.OnClickListener() {
            @Override
            public void onClick(View v) {
                View customView = LayoutInflater.from(ctx).inflate(R.layout.txdetailspopup, cLayout, false);
                PopupWindow popupWindow = new PopupWindow(customView, CoordinatorLayout.LayoutParams.WRAP_CONTENT, CoordinatorLayout.LayoutParams.WRAP_CONTENT, true);
                popupWindow.showAtLocation(cLayout, Gravity.CENTER, 0, 0);
                popupWindow.setOutsideTouchable(true);
                popupWindow.setFocusable(true);
                popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

                ImageView arrow = customView.findViewById(R.id.txDetailsArrow);
                if (tx.getIncoming()) {
                    arrow.setImageResource(R.drawable.receive_arrow);
                } else {
                    arrow.setImageResource(R.drawable.send_arrow);
                }
                TextView bchAmount = customView.findViewById(R.id.txDetailsBchAmount);
                String detailsAmount = ctx.getString(R.string.bch_amount, new Amount(tx.getAmount()).toString());
                bchAmount.setText(detailsAmount);

                TextView fiatAmount = customView.findViewById(R.id.txDetailsFiatAmount);
                fiatAmount.setText(tx.getFiatAmount());

                TextView dateView = customView.findViewById(R.id.txDetailsDate);
                // In the phone's language and its 12 or 24 hour clock.
                dateView.setText(DateUtils.formatDateTime(ctx, tx.getTimestamp() * 1000, DateUtils.FORMAT_SHOW_DATE
                        | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_ABBREV_MONTH));

                TextView status = customView.findViewById(R.id.txDetailsStatus);
                ImageView circle = customView.findViewById(R.id.confirmationCircle);

                int confirmations = 0;
                if (tx.getHeight() > 0) {
                    confirmations = blockHeight - tx.getHeight() + 1;
                }
                if (confirmations <= 0) {
                    status.setText(R.string.unconfirmed);
                    circle.setImageResource(R.drawable.red_circle);
                } else if (confirmations < 10) {
                    status.setText(R.string.pending);
                    circle.setImageResource(R.drawable.yellow_circle);
                } else {
                    status.setText(R.string.confirmed);
                    circle.setImageResource(R.drawable.green_circle);
                }

                String to = tx.getIncoming() ? null : recipients(tx);
                if (to == null) {
                    customView.findViewById(R.id.txDetailsToDivider).setVisibility(View.GONE);
                    customView.findViewById(R.id.txDetailsToRow).setVisibility(View.GONE);
                } else {
                    TextView toView = customView.findViewById(R.id.txDetailsTo);
                    toView.setText(to);
                }

                TextView memo = customView.findViewById(R.id.txDetailsMemo);
                memo.setText(tx.getMemo());

                TextView txid = customView.findViewById(R.id.txDetailsTxid);
                txid.setText(tx.getTxid());

                TextView link = customView.findViewById(R.id.txDetailsLink);
                link.setText(network.explorerUrl(tx.getTxid()));
            }
        });
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return mDataset.size();
    }

    // sentTo says whom a sent transaction paid in the width of the view: the address it paid,
    // shortened in its middle so that its start and end stay in view, or how many it paid, since
    // a row has no room for an address beside the count.
    private String sentTo(TransactionData tx, TextView view) {
        switch (tx.getSentTo()) {
            case YOURSELF:
                return ctx.getString(R.string.sent_to_yourself);
            case NO_ADDRESS:
                return ctx.getString(R.string.sent);
            case ADDRESS_AND_MORE:
                int addresses = tx.getMoreRecipients() + 1;
                return ctx.getResources().getQuantityString(R.plurals.sent_to_addresses, addresses, addresses);
            default:
                return ctx.getString(R.string.sent_to,
                        shorten(tx.getToAddress(), ctx.getString(R.string.sent_to, ""), view));
        }
    }

    // shorten shortens an address in its middle to fit beside the text around it in the view's
    // width, or leaves it whole if the view has no fixed width.
    private static CharSequence shorten(String address, String around, TextView view) {
        int width = view.getLayoutParams().width - view.getTotalPaddingLeft() - view.getTotalPaddingRight();
        if (width <= 0) {
            return address;
        }
        TextPaint paint = view.getPaint();
        return TextUtils.ellipsize(address, paint, width - paint.measureText(around), TextUtils.TruncateAt.MIDDLE);
    }

    // recipients says in full whom a sent transaction paid, or is null if no output that pays
    // someone else pays an address.
    private String recipients(TransactionData tx) {
        switch (tx.getSentTo()) {
            case YOURSELF:
                return ctx.getString(R.string.tx_details_to_yourself);
            case NO_ADDRESS:
                return null;
            case ADDRESS_AND_MORE:
                return ctx.getResources().getQuantityString(R.plurals.tx_details_to_and_more,
                        tx.getMoreRecipients(), tx.getToAddress(), tx.getMoreRecipients());
            default:
                return tx.getToAddress();
        }
    }
}