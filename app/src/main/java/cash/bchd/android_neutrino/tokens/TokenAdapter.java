package cash.bchd.android_neutrino.tokens;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.List;

import cash.bchd.android_neutrino.R;
import cash.bchd.android_neutrino.wallet.TokenBalance;

/**
 * TokenAdapter lists the wallet's tokens on the main screen's Tokens page, a row for each
 * category: its identicon, its short token ID, and its fungible amount, its NFTs, or both.
 */
public final class TokenAdapter extends RecyclerView.Adapter<TokenAdapter.Row> {

    /**
     * OnClick is told of the token whose row was tapped.
     */
    public interface OnClick {
        void onClick(TokenBalance token);
    }

    private final Context context;
    private final OnClick onClick;
    private final int iconSize;
    // The identicons drawn so far, by token ID.
    private final LruCache<String, Bitmap> icons = new LruCache<>(64);
    private List<TokenBalance> tokens = Collections.emptyList();

    public static final class Row extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView id;
        final TextView amount;

        Row(View v) {
            super(v);
            icon = v.findViewById(R.id.tokenIcon);
            id = v.findViewById(R.id.tokenId);
            amount = v.findViewById(R.id.tokenAmount);
        }
    }

    public TokenAdapter(Context context, OnClick onClick) {
        this.context = context;
        this.onClick = onClick;
        iconSize = context.getResources().getDimensionPixelSize(R.dimen.token_row_icon);
    }

    /**
     * setTokens shows the tokens, adding, removing, moving and redrawing only the rows that
     * changed, which the list animates.
     */
    public void setTokens(List<TokenBalance> tokens) {
        List<TokenBalance> shown = this.tokens;
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return shown.size();
            }

            @Override
            public int getNewListSize() {
                return tokens.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPosition, int newPosition) {
                return shown.get(oldPosition).category.equals(tokens.get(newPosition).category);
            }

            @Override
            public boolean areContentsTheSame(int oldPosition, int newPosition) {
                return shown.get(oldPosition).equals(tokens.get(newPosition));
            }
        });
        this.tokens = tokens;
        diff.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public Row onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Row(LayoutInflater.from(parent.getContext()).inflate(R.layout.token_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Row row, int position) {
        TokenBalance token = tokens.get(position);
        row.icon.setImageBitmap(icon(token.category));
        row.id.setText(TokenBalance.shortId(token.category));
        row.amount.setText(amount(token));
        row.itemView.setOnClickListener(v -> onClick.onClick(token));
    }

    @Override
    public int getItemCount() {
        return tokens.size();
    }

    private Bitmap icon(String category) {
        Bitmap icon = icons.get(category);
        if (icon == null) {
            icon = Identicon.of(category).bitmap(iconSize);
            icons.put(category, icon);
        }
        return icon;
    }

    // amount says what the wallet holds of a category: its fungible amount, in the token's
    // smallest units, its NFTs, or both.
    private String amount(TokenBalance token) {
        String nfts = token.nfts == 0 ? null
                : context.getResources().getQuantityString(R.plurals.token_nfts, token.nfts, token.nfts);
        if (token.amount == 0) {
            return nfts;
        }
        String amount = NumberFormat.getIntegerInstance().format(new BigInteger(Long.toUnsignedString(token.amount)));
        return nfts == null ? amount : context.getString(R.string.token_amount_and_nfts, amount, nfts);
    }
}
