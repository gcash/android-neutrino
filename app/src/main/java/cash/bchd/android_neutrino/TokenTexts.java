package cash.bchd.android_neutrino;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.StringRes;

import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import cash.bchd.android_neutrino.tokens.NftAdapter;
import cash.bchd.android_neutrino.tokens.TokenLabels;
import cash.bchd.android_neutrino.wallet.TokenChange;

/**
 * TokenTexts says how a transaction changed one of the wallet's tokens, as the rows of the
 * transaction lists and the transaction's details show it.
 */
final class TokenTexts {

    private TokenTexts() {
    }

    /**
     * change says how the transaction changed the token: its fungible amount, its NFTs, or both,
     * each with its sign.
     */
    static String change(Context ctx, TokenChange change) {
        String amount = change.amount == 0 ? null : signed(change.category, change.amount);
        String nfts = change.nfts == 0 ? null : nfts(ctx, change.nfts);
        if (amount == null) {
            return nfts;
        }
        return nfts == null ? amount : ctx.getString(R.string.token_amount_and_nfts, amount, nfts);
    }

    /**
     * changeOnTokenScreen says the same where the token's symbol is plain, on the token's own
     * screen, which leaves room for the row's date.
     */
    static String changeOnTokenScreen(Context ctx, TokenChange change) {
        String amount = change.amount == 0 ? null
                : (change.amount > 0 ? "+" : "") + TokenLabels.number(change.category, BigInteger.valueOf(change.amount));
        String nfts = change.nfts == 0 ? null : nfts(ctx, change.nfts);
        if (amount == null) {
            return nfts;
        }
        return nfts == null ? amount : ctx.getString(R.string.token_amount_and_nfts, amount, nfts);
    }

    /**
     * details says how the transaction changed the token apart from the NFTs that have rows of their
     * own: its fungible amount, or how many NFTs for a change saved before the app kept which.
     */
    static String details(Context ctx, TokenChange change) {
        List<String> lines = new ArrayList<>();
        if (change.amount != 0) {
            lines.add(signed(change.category, change.amount));
        }
        if (change.nfts != 0 && change.nftsReceived().isEmpty() && change.nftsSent().isEmpty()) {
            lines.add(nfts(ctx, change.nfts));
        }
        return TextUtils.join("\n", lines);
    }

    /**
     * nftIn says an NFT came, by its name if it has one to trust, or else its commitment.
     */
    static String nftIn(Context ctx, String category, String commitment) {
        return nftLine(ctx, category, commitment, R.string.nft_in, R.string.nft_in_with, R.string.nft_in_named);
    }

    /**
     * nftOut says an NFT went, as nftIn says one came.
     */
    static String nftOut(Context ctx, String category, String commitment) {
        return nftLine(ctx, category, commitment, R.string.nft_out, R.string.nft_out_with, R.string.nft_out_named);
    }

    private static String nftLine(Context ctx, String category, String commitment, @StringRes int bare,
                                  @StringRes int withCommitment, @StringRes int named) {
        String name = TokenLabels.listNftName(category, commitment);
        if (name != null) {
            return ctx.getString(named, name);
        }
        return commitment.isEmpty() ? ctx.getString(bare)
                : ctx.getString(withCommitment, NftAdapter.shortCommitment(commitment));
    }

    private static String nfts(Context ctx, int nfts) {
        return ctx.getResources().getQuantityString(R.plurals.token_nfts_change, Math.abs(nfts), signed(nfts));
    }

    private static String signed(String category, long n) {
        return (n > 0 ? "+" : "") + TokenLabels.amount(category, BigInteger.valueOf(n));
    }

    private static String signed(long n) {
        return (n > 0 ? "+" : "-") + NumberFormat.getIntegerInstance().format(Math.abs(n));
    }
}
