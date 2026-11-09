package cash.bchd.android_neutrino.tokens;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cash.bchd.android_neutrino.R;
import cash.bchd.android_neutrino.wallet.TokenNft;

/**
 * NftAdapter lists the NFTs of a token's collection, a row for each: its art, its name or its
 * commitment, shortened when it's long, and what it can do, Minting or Mutable, which an immutable
 * NFT shows nothing for. With the token's images on, it loads the art of the rows that show.
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
    private final String category;
    private final int iconSize;
    private final NftArt loader;
    private List<TokenNft> nfts = Collections.emptyList();

    public static final class Row extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView commitment;
        final TextView capability;

        Row(View v) {
            super(v);
            icon = v.findViewById(R.id.nftIcon);
            commitment = v.findViewById(R.id.nftCommitment);
            capability = v.findViewById(R.id.nftCapability);
        }
    }

    public NftAdapter(Context context, String category, OnClick onClick) {
        this.category = category;
        this.context = context;
        this.onClick = onClick;
        loader = new NftArt(context, (loadedCategory, commitment) -> artLoaded(commitment));
        iconSize = context.getResources().getDimensionPixelSize(R.dimen.token_row_icon);
    }

    /**
     * iconsChanged redraws the rows with the images that were loaded.
     */
    public void iconsChanged() {
        loader.retry();
        notifyItemRangeChanged(0, nfts.size());
    }

    /**
     * stop ends the loading of art, as the screen is going.
     */
    public void stop() {
        loader.stop();
    }

    private void artLoaded(String commitment) {
        for (int i = 0; i < nfts.size(); i++) {
            if (nfts.get(i).commitment.equals(commitment)) {
                notifyItemChanged(i);
            }
        }
    }

    /**
     * setNfts shows the NFTs, adding, removing, moving and redrawing only the rows that changed.
     */
    public void setNfts(List<TokenNft> unsorted) {
        List<TokenNft> nfts = new ArrayList<>(unsorted);
        nfts.sort(this::compareNames);
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

    /**
     * labelsChanged sorts and redraws the rows, as the wallet said more of the NFTs.
     */
    public void labelsChanged() {
        setNfts(nfts);
        notifyItemRangeChanged(0, nfts.size());
    }

    // compareNames puts the NFTs with names first, in the order of their names, and leaves the
    // rest as they came.
    private int compareNames(TokenNft a, TokenNft b) {
        String nameA = TokenLabels.nftName(category, a.commitment);
        String nameB = TokenLabels.nftName(category, b.commitment);
        if (nameA == null || nameB == null) {
            return Boolean.compare(nameA == null, nameB == null);
        }
        return compareNatural(nameA, nameB);
    }

    /**
     * compareNatural orders names as a person reads them, with a run of digits as its number, so
     * that Puffer #192 comes before Puffer #1144.
     */
    static int compareNatural(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            if (isDigit(a.charAt(i)) && isDigit(b.charAt(j))) {
                int endA = digitsEnd(a, i);
                int endB = digitsEnd(b, j);
                int order = new BigInteger(a.substring(i, endA)).compareTo(new BigInteger(b.substring(j, endB)));
                if (order != 0) {
                    return order;
                }
                i = endA;
                j = endB;
            } else {
                int order = Character.compare(Character.toLowerCase(a.charAt(i)), Character.toLowerCase(b.charAt(j)));
                if (order != 0) {
                    return order;
                }
                i++;
                j++;
            }
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static int digitsEnd(String text, int start) {
        int end = start;
        while (end < text.length() && isDigit(text.charAt(end))) {
            end++;
        }
        return end;
    }

    @NonNull
    @Override
    public Row onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Row(LayoutInflater.from(parent.getContext()).inflate(R.layout.nft_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Row row, int position) {
        TokenNft nft = nfts.get(position);
        TokenIcons.showNft(row.icon, category, nft.commitment, iconSize);
        loader.request(category, nft.commitment);
        String name = TokenLabels.nftName(category, nft.commitment);
        if (name != null) {
            row.commitment.setTypeface(Typeface.DEFAULT);
            row.commitment.setText(name);
        } else if (nft.commitment.isEmpty()) {
            row.commitment.setText(null);
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
