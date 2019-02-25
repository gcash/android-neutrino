package cash.bchd.android_neutrino;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.util.List;

import cash.bchd.android_neutrino.wallet.Amount;
import cash.bchd.android_neutrino.wallet.TransactionData;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TxViewHolder> {
    private List<TransactionData> mDataset;
    Context ctx;
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
        public TxViewHolder(LinearLayout v) {
            super(v);
            bchAmount = (TextView) v.findViewById(R.id.bchAmount);
            fiatAmount = (TextView) v.findViewById(R.id.fiatAmount);
            txDescription = (TextView) v.findViewById(R.id.txDescription);
            txMemo = (TextView) v.findViewById(R.id.txMemo);
            arrowImage = (ImageView) v.findViewById(R.id.arrowImage);
            confirmationCircle = (ImageView) v.findViewById(R.id.confirmationCircle);
        }
    }

    // Provide a suitable constructor (depends on the kind of dataset)
    public TransactionAdapter(List<TransactionData> myDataset, Context context, int height) {
        mDataset = myDataset;
        ctx = context;
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
        } else if (confirmations < 10) {
            holder.confirmationCircle.setImageResource(R.drawable.yellow_circle);
            String confirmedText = confirmations + " Confirmations";
            holder.fiatAmount.setText(confirmedText);
        } else {
            holder.confirmationCircle.setVisibility(View.GONE);
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
            if (tx.getMemo().equals("")){
                String to = "Sent to " + tx.getToAddress();
                holder.txMemo.setText(to);
            } else {
                holder.txMemo.setText(tx.getMemo());
            }
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return mDataset.size();
    }
}