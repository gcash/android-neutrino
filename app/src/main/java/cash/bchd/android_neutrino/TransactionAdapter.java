package cash.bchd.android_neutrino;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.support.design.widget.CoordinatorLayout;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import cash.bchd.android_neutrino.wallet.Amount;
import cash.bchd.android_neutrino.wallet.TransactionData;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TxViewHolder> {
    private List<TransactionData> mDataset;
    Context ctx;
    CoordinatorLayout cLayout;
    int blockHeight;

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
            bchAmount = (TextView) v.findViewById(R.id.bchAmount);
            fiatAmount = (TextView) v.findViewById(R.id.fiatAmount);
            txDescription = (TextView) v.findViewById(R.id.txDescription);
            txMemo = (TextView) v.findViewById(R.id.txMemo);
            arrowImage = (ImageView) v.findViewById(R.id.arrowImage);
            confirmationCircle = (ImageView) v.findViewById(R.id.confirmationCircle);
        }
    }

    // Provide a suitable constructor (depends on the kind of dataset)
    public TransactionAdapter(List<TransactionData> myDataset, Context context, CoordinatorLayout layout, int height) {
        mDataset = myDataset;
        ctx = context;
        cLayout = layout;
        blockHeight = height;
    }

    public void setNewData(List<TransactionData> data) {
        mDataset = data;
    }

    public List<TransactionData> getData() {
        return mDataset;
    }

    public void setBlockHeight(int height) {
        blockHeight = height;
    }

    public void updateOrInsertTx(TransactionData newTx) {
        int i = 0;
        boolean found = false;
        for (TransactionData tx : mDataset) {
            if (tx.getTxid().equals(newTx.getTxid())) {
                tx.setHeight(newTx.getHeight());
                if (tx.getFiatAmount().equals("")) {
                    tx.setFiatAmount(newTx.getFiatAmount());
                }
                if (tx.getAmount() == 0) {
                    tx.setAmount(newTx.getAmount());
                }
                mDataset.set(i, tx);
                found = true;
                break;
            }
            i++;
        }
        if (!found) {
            mDataset.add(0, newTx);
        }
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

        int confirmations = 0;
        if (tx.getHeight() > 0) {
            confirmations = blockHeight - tx.getHeight() + 1;
        }
        if (confirmations <= 0) {
            holder.fiatAmount.setText("Unconfirmed");
            holder.confirmationCircle.setImageResource(R.drawable.red_circle);
            holder.confirmationCircle.setVisibility(View.VISIBLE);
        } else if (confirmations < 10) {
            holder.confirmationCircle.setImageResource(R.drawable.yellow_circle);
            holder.confirmationCircle.setVisibility(View.VISIBLE);
            String confirmedText = confirmations + " Confirmations";
            holder.fiatAmount.setText(confirmedText);
        } else {
            holder.confirmationCircle.setVisibility(View.INVISIBLE);
            holder.fiatAmount.setText(tx.getFiatAmount());
        }

        if (tx.getIncoming()) {
            holder.txDescription.setText("Received Bitcoin Cash");
            holder.arrowImage.setImageResource(R.drawable.receive_arrow);
            holder.bchAmount.setTextColor(ctx.getResources().getColor(R.color.darkGreen));
            if (tx.getMemo().equals("")){
                holder.txMemo.setText("From Bitcoin Cash Address");
            } else {
                holder.txMemo.setText(tx.getMemo());
            }
        } else {
            holder.arrowImage.setImageResource(R.drawable.send_arrow);
            holder.txDescription.setText("Sent Bitcoin Cash");
            holder.bchAmount.setTextColor(ctx.getResources().getColor(R.color.neonPurple));
            if (tx.getMemo() == null || tx.getMemo().equals("")){
                String to = "Sent to " + tx.getToAddress();
                holder.txMemo.setText(to);
            } else {
                holder.txMemo.setText(tx.getMemo());
            }
        }

        holder.layout.setOnClickListener(new LinearLayout.OnClickListener(){
            @Override
            public void onClick(View v) {
                View customView = LayoutInflater.from(ctx).inflate(R.layout.txdetailspopup, null);
                PopupWindow popupWindow = new PopupWindow(customView, CoordinatorLayout.LayoutParams.WRAP_CONTENT, CoordinatorLayout.LayoutParams.WRAP_CONTENT, true);
                popupWindow.showAtLocation(cLayout, Gravity.CENTER, 0, 0);
                popupWindow.setOutsideTouchable(true);
                popupWindow.setFocusable(true);
                popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

                ImageView arrow = (ImageView) customView.findViewById(R.id.txDetailsArrow);
                if (tx.getIncoming()) {
                    arrow.setImageResource(R.drawable.receive_arrow);
                } else {
                    arrow.setImageResource(R.drawable.send_arrow);
                }
                TextView bchAmount = (TextView) customView.findViewById(R.id.txDetailsBchAmount);
                String detailsAmount = new Amount(tx.getAmount()).toString() + " BCH";
                bchAmount.setText(detailsAmount);

                TextView fiatAmount = (TextView) customView.findViewById(R.id.txDetailsFiatAmount);
                fiatAmount.setText(tx.getFiatAmount());

                TextView dateView = (TextView) customView.findViewById(R.id.txDetailsDate);
                Date date = new Date(tx.getTimestamp());
                DateFormat formatter = new SimpleDateFormat("hh:mm a - MMM dd, yyyy");
                String dateFormatted = formatter.format(date);
                dateView.setText(dateFormatted);

                TextView status = (TextView) customView.findViewById(R.id.txDetailsStatus);
                ImageView circle = (ImageView) customView.findViewById(R.id.confirmationCircle);

                int confirmations = 0;
                if (tx.getHeight() > 0) {
                    confirmations = blockHeight - tx.getHeight() + 1;
                }
                if (confirmations <= 0) {
                    status.setText("Unconfirmed");
                    circle.setImageResource(R.drawable.red_circle);
                } else if (confirmations < 10) {
                    status.setText("Pending");
                    circle.setImageResource(R.drawable.yellow_circle);
                } else {
                    status.setText("Confirmed");
                    circle.setImageResource(R.drawable.green_circle);
                }

                TextView memo = (TextView) customView.findViewById(R.id.txDetailsMemo);
                memo.setText(tx.getMemo());

                TextView txid = (TextView) customView.findViewById(R.id.txDetailsTxid);
                txid.setText(tx.getTxid());

                TextView link = (TextView) customView.findViewById(R.id.txDetailsLink);
                String url = "https://www.blockchain.com/bch/tx/" + tx.getTxid();
                link.setText(url);
            }
        });

    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return mDataset.size();
    }
}