package cash.bchd.android_neutrino;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cash.bchd.android_neutrino.tokens.NftArt;
import cash.bchd.android_neutrino.tokens.TokenIcons;
import cash.bchd.android_neutrino.tokens.TokenLabels;
import cash.bchd.android_neutrino.wallet.Amount;
import cash.bchd.android_neutrino.wallet.Network;
import cash.bchd.android_neutrino.wallet.TokenBalance;
import cash.bchd.android_neutrino.wallet.TokenChange;
import cash.bchd.android_neutrino.wallet.TokenMeta;
import cash.bchd.android_neutrino.wallet.TransactionData;
import cash.bchd.android_neutrino.wallet.Wallet;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TxViewHolder> {

    private List<TransactionData> mDataset;
    private Context ctx;
    private CoordinatorLayout cLayout;
    private int blockHeight;
    // The network of the transactions.
    private final Network network;
    // What the rows showed when the transactions or the block height were last set.
    private List<TransactionRows.Row> shownRows;
    // The popup with a transaction's details, and the transaction, while it shows.
    private PopupWindow detailsPopup;
    private TransactionData detailsTx;
    // The art of the NFTs that rows lead with, and the tokens whose NFTs were asked about.
    private final NftArt art;
    private final Set<String> askedTypes = new HashSet<>();
    // The icons that rows lead with, by token ID, with the commitment for an NFT's.
    private final LruCache<String, Bitmap> icons = new LruCache<>(64);

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    public static class TxViewHolder extends RecyclerView.ViewHolder {
        TextView bchAmount;
        TextView fiatAmount;
        TextView txDescription;
        TextView txMemo;
        ImageView arrowImage;
        LinearLayout layout;
        public TxViewHolder(LinearLayout v) {
            super(v);
            layout = v;
            bchAmount = v.findViewById(R.id.bchAmount);
            fiatAmount = v.findViewById(R.id.fiatAmount);
            txDescription = v.findViewById(R.id.txDescription);
            txMemo = v.findViewById(R.id.txMemo);
            arrowImage = v.findViewById(R.id.arrowImage);
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
        art = new NftArt(context, this::artLoaded);
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
        holder.layout.setOnClickListener(v -> showDetails(tx));
        if (!tx.getTokens().isEmpty()) {
            bindTokens(holder, tx);
            return;
        }
        // A token's row, which this one may be reused from, draws its icon unscaled.
        holder.arrowImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        String bch = new Amount(tx.getAmount()).withSymbol();
        holder.bchAmount.setText(bch);

        // The status shows a dot before it until the transaction has ten confirmations.
        int confirmations = TransactionRows.confirmations(tx, blockHeight);
        if (confirmations <= 0) {
            holder.fiatAmount.setText(R.string.unconfirmed);
            holder.fiatAmount.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.red_circle, 0, 0, 0);
        } else if (confirmations < 10) {
            holder.fiatAmount.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.yellow_circle, 0, 0, 0);
            holder.fiatAmount.setText(ctx.getResources().getQuantityString(R.plurals.confirmations, confirmations, confirmations));
        } else {
            holder.fiatAmount.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
            holder.fiatAmount.setText(tx.getFiatAmount());
        }

        holder.txMemo.setEllipsize(TextUtils.TruncateAt.END);
        if (tx.getIncoming()) {
            holder.txDescription.setText(R.string.received_bitcoin_cash);
            holder.arrowImage.setImageResource(R.drawable.receive_arrow);
            holder.bchAmount.setTextColor(ContextCompat.getColor(ctx, R.color.darkGreen));
            if (tx.getMemo().equals("")) {
                holder.txMemo.setText(R.string.from_bitcoin_cash_address);
            } else {
                holder.txMemo.setText(tx.getMemo());
            }
        } else {
            holder.arrowImage.setImageResource(R.drawable.send_arrow);
            holder.txDescription.setText(R.string.sent_bitcoin_cash);
            holder.bchAmount.setTextColor(ContextCompat.getColor(ctx, R.color.neonPurple));
            if (tx.getMemo() == null || tx.getMemo().equals("")) {
                holder.txMemo.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                holder.txMemo.setText(sentTo(tx, holder.txMemo));
            } else {
                holder.txMemo.setText(tx.getMemo());
            }
        }
    }

    // bindTokens draws the row of a transaction that moved tokens, which leads with the first
    // token it moved: its icon, whether the wallet received or sent it, its memo or the
    // token's short ID, with how many more tokens it moved, and how much of the token. Below the
    // amount are the confirmations, then, once they're ten, the change of the spendable balance,
    // if it's the floor or more either way, as the BCH that tokens carry isn't spendable.
    private void bindTokens(TxViewHolder holder, TransactionData tx) {
        List<TokenChange> tokens = tx.getTokens();
        TokenChange first = tokens.get(0);
        boolean received = first.received();
        boolean nftsOnly = first.amount == 0;
        holder.arrowImage.setScaleType(ImageView.ScaleType.CENTER);
        holder.arrowImage.setImageBitmap(icon(tokens));
        holder.txDescription.setText(received ? (nftsOnly ? R.string.received_nft : R.string.received_token)
                : (nftsOnly ? R.string.sent_nft : R.string.sent_token));
        holder.txMemo.setEllipsize(TextUtils.TruncateAt.END);
        String what = tx.getMemo() == null || tx.getMemo().isEmpty() ? TokenLabels.listTitle(first.category) : tx.getMemo();
        int more = tokens.size() - 1;
        holder.txMemo.setText(more > 0 ? ctx.getResources().getQuantityString(R.plurals.token_row_more, more, what, more) : what);
        holder.bchAmount.setText(TokenTexts.change(ctx, first));
        holder.bchAmount.setTextColor(ContextCompat.getColor(ctx, received ? R.color.darkGreen : R.color.neonPurple));

        int confirmations = TransactionRows.confirmations(tx, blockHeight);
        if (confirmations <= 0) {
            holder.fiatAmount.setText(R.string.unconfirmed);
            holder.fiatAmount.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.red_circle, 0, 0, 0);
        } else if (confirmations < 10) {
            holder.fiatAmount.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.yellow_circle, 0, 0, 0);
            holder.fiatAmount.setText(ctx.getResources().getQuantityString(R.plurals.confirmations, confirmations, confirmations));
        } else {
            holder.fiatAmount.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
            long spendable = tx.getSpendableAmount();
            String bch = Math.abs(spendable) >= TokenBalance.FLOOR ? new Amount(spendable).withSymbol() : null;
            holder.fiatAmount.setText(bch);
        }
    }

    private void artLoaded(String category, String commitment) {
        icons.remove(category + "-" + commitment);
        for (int i = 0; i < mDataset.size(); i++) {
            List<TokenChange> tokens = mDataset.get(i).getTokens();
            if (tokens.size() == 1 && tokens.get(0).category.equals(category)
                    && commitment.equals(tokens.get(0).onlyNft())) {
                notifyItemChanged(i);
            }
        }
    }

    // icon returns what a row leads with: the art of the NFT if the transaction moved just that,
    // or else the token's icon.
    private Bitmap icon(List<TokenChange> tokens) {
        TokenChange change = tokens.get(0);
        String category = change.category;
        String commitment = tokens.size() == 1 ? change.onlyNft() : null;
        if (commitment != null) {
            loadArt(category, commitment);
        }
        String key = commitment == null ? category : category + "-" + commitment;
        Bitmap icon = icons.get(key);
        if (icon == null) {
            int size = ctx.getResources().getDimensionPixelSize(R.dimen.token_row_icon);
            icon = commitment == null ? TokenIcons.bitmap(ctx, category, size)
                    : TokenIcons.nftBitmap(ctx, category, commitment, size);
            icons.put(key, icon);
        }
        return icon;
    }

    // loadArt has the art of an NFT load, asking the wallet what its token's NFTs are first, as it
    // only says for a few tokens at a time.
    private void loadArt(String category, String commitment) {
        if (!TokenIcons.showing(category)) {
            return;
        }
        if (TokenLabels.hasNftTypes(category)) {
            art.request(category, commitment);
            return;
        }
        Wallet wallet = Wallet.getInstance();
        if (wallet == null || !wallet.isRunning() || !askedTypes.add(category)) {
            return;
        }
        Futures.addCallback(wallet.tokenMetadataAsync(Collections.singletonList(category), true),
                new FutureCallback<List<TokenMeta>>() {
                    @Override
                    public void onSuccess(List<TokenMeta> tokens) {
                        TokenLabels.update(tokens);
                        cLayout.post(() -> notifyItemRangeChanged(0, getItemCount()));
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        t.printStackTrace();
                        cLayout.post(() -> askedTypes.remove(category));
                    }
                }, MoreExecutors.directExecutor());
    }

    /**
     * labelsChanged has the rows ask again what their tokens' NFTs are, as the wallet may only now
     * have them.
     */
    void labelsChanged() {
        askedTypes.clear();
        notifyItemRangeChanged(0, getItemCount());
    }

    /**
     * stop ends the loading of art, as the screen is going.
     */
    void stop() {
        art.stop();
    }

    /**
     * iconsChanged redraws the rows with the icons that were loaded.
     */
    void iconsChanged() {
        icons.evictAll();
        notifyItemRangeChanged(0, getItemCount());
    }

    /**
     * showDetails shows a transaction's details in a popup.
     */
    void showDetails(TransactionData tx) {
        detailsPopup = TransactionDetailsPopup.show(ctx, cLayout, tx, blockHeight, network);
        detailsTx = tx;
    }

    /**
     * shownDetails returns the transaction whose details show, or null, for a recreated screen
     * to show them again.
     */
    TransactionData shownDetails() {
        return detailsPopup != null && detailsPopup.isShowing() ? detailsTx : null;
    }

    /**
     * dismissDetails closes the popup with a transaction's details, if it shows.
     */
    void dismissDetails() {
        if (detailsPopup != null) {
            detailsPopup.dismiss();
        }
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
}