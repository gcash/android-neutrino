package cash.bchd.android_neutrino.tokens;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;

import cash.bchd.android_neutrino.R;
import cash.bchd.android_neutrino.wallet.TokenNft;

/**
 * NftAdapter lists the NFTs of a token's collection, a row for each: its commitment, shortened
 * when it's long, and what it can do, Minting or Mutable, which an immutable NFT shows nothing for.
 */
public final class NftAdapter extends RecyclerView.Adapter<NftAdapter.Row> {

    /**
     * OnClick is told of the NFT whose row was tapped.
     */
    public interface OnClick {
        void onClick(TokenNft nft);
    }

    private final Context context;
    private final OnClick onClick;
    private List<TokenNft> nfts = Collections.emptyList();

    public static final class Row extends RecyclerView.ViewHolder {
        final TextView commitment;
        final TextView capability;

        Row(View v) {
            super(v);
            commitment = v.findViewById(R.id.nftCommitment);
            capability = v.findViewById(R.id.nftCapability);
        }
    }

    public NftAdapter(Context context, OnClick onClick) {
        this.context = context;
        this.onClick = onClick;
    }

    /**
     * setNfts shows the NFTs, adding, removing, moving and redrawing only the rows that changed.
     */
    public void setNfts(List<TokenNft> nfts) {
        List<TokenNft> shown = this.nfts;
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return shown.size();
            }

            @Override
            public int getNewListSize() {
                return nfts.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPosition, int newPosition) {
                TokenNft before = shown.get(oldPosition);
                TokenNft after = nfts.get(newPosition);
                return before.txid.equals(after.txid) && before.index == after.index;
            }

            @Override
            public boolean areContentsTheSame(int oldPosition, int newPosition) {
                return shown.get(oldPosition).equals(nfts.get(newPosition));
            }
        });
        this.nfts = nfts;
        diff.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public Row onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Row(LayoutInflater.from(parent.getContext()).inflate(R.layout.nft_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Row row, int position) {
        TokenNft nft = nfts.get(position);
        if (nft.commitment.isEmpty()) {
            row.commitment.setTypeface(Typeface.DEFAULT);
            row.commitment.setText(R.string.nft_no_commitment);
        } else {
            row.commitment.setTypeface(Typeface.MONOSPACE);
            row.commitment.setText(shortCommitment(nft.commitment));
        }
        switch (nft.capability) {
            case MINTING:
                row.capability.setText(R.string.nft_label_minting);
                break;
            case MUTABLE:
                row.capability.setText(R.string.nft_label_mutable);
                break;
            default:
                row.capability.setText(null);
        }
        row.itemView.setOnClickListener(v -> onClick.onClick(nft));
    }

    @Override
    public int getItemCount() {
        return nfts.size();
    }

    /**
     * shortCommitment leaves a commitment of up to 16 digits, 8 bytes, whole, which numbers a
     * collection's NFTs, and shortens a longer one to its first six and last four digits.
     */
    public static String shortCommitment(String commitment) {
        if (commitment.length() <= 16) {
            return commitment;
        }
        return commitment.substring(0, 6) + "…" + commitment.substring(commitment.length() - 4);
    }
}
