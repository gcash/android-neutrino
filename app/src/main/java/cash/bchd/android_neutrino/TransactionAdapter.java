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

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    public static class TxViewHolder extends RecyclerView.ViewHolder {
        TextView bchAmount;
        TextView fiatAmount;
        TextView txDescription;
        TextView txMemo;
        ImageView arrowImage;
        public TxViewHolder(LinearLayout v) {
            super(v);
            bchAmount = (TextView) v.findViewById(R.id.bchAmount);
            fiatAmount = (TextView) v.findViewById(R.id.fiatAmount);
            txDescription = (TextView) v.findViewById(R.id.txDescription);
            txMemo = (TextView) v.findViewById(R.id.txMemo);
            arrowImage = (ImageView) v.findViewById(R.id.arrowImage);
        }
    }

    // Provide a suitable constructor (depends on the kind of dataset)
    public TransactionAdapter(List<TransactionData> myDataset, Context context) {
        mDataset = myDataset;
        ctx = context;
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
        String bch = new Amount(tx.getAmount()).toString() + " BCH";
        holder.bchAmount.setText(bch);
        holder.fiatAmount.setText(tx.getFiatAmount());

        if (tx.getIncoming()) {
            holder.txDescription.setText("Received Bitcoin Cash");
            holder.arrowImage.setImageResource(R.drawable.receive_arrow);
            holder.bchAmount.setTextColor(ctx.getResources().getColor(R.color.neonGreen));
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