package cash.bchd.android_neutrino;

import android.content.Context;
import android.text.TextUtils;

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
        String nfts = nftsChange(ctx, change);
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
        String amount = change.amount == 0 ? null : signedNumber(change.category, change.amount);
        String nfts = nftsChange(ctx, change);
        if (amount == null) {
            return nfts;
        }
        return nfts == null ? amount : ctx.getString(R.string.token_amount_and_nfts, amount, nfts);
    }

    // nftsChange says how the NFTs changed, or both ways if as many came as went, or is null.
    private static String nftsChange(Context ctx, TokenChange change) {
        if (change.nfts != 0) {
            return nfts(ctx, change.nfts);
        }
        int swapped = change.nftsReceived().size();
        return swapped == 0 ? null
                : ctx.getString(R.string.token_amount_and_nfts, nfts(ctx, swapped), nfts(ctx, -swapped));
    }

    /**
     * details says how the transaction changed the token apart from the NFTs that have rows of their
     * own: its fungible amount, without the symbol, which the row has under the token's name, or
     * how many NFTs for a change saved before the app kept which.
     */
    static String details(Context ctx, TokenChange change) {
        List<String> lines = new ArrayList<>();
        if (change.amount != 0) {
            lines.add(signedNumber(change.category, change.amount));
        }
        if (change.nfts != 0 && change.nftsReceived().isEmpty() && change.nftsSent().isEmpty()) {
            lines.add(nfts(ctx, change.nfts));
        }
        return TextUtils.join("\n", lines);
    }

    /**
     * nftTitle says what to call an NFT in the transaction's details: its name if it has one to
     * trust, or else its commitment.
     */
    static String nftTitle(Context ctx, String category, String commitment) {
        String name = TokenLabels.listNftName(category, commitment);
        if (name != null) {
            return name;
        }
        return commitment.isEmpty() ? ctx.getString(R.string.nft_untitled)
                : ctx.getString(R.string.nft_with_commitment, NftAdapter.shortCommitment(commitment));
    }

    /**
     * nftChange says that an NFT came or went.
     */
    static String nftChange(Context ctx, boolean in) {
        return nfts(ctx, in ? 1 : -1);
    }

    private static String nfts(Context ctx, int nfts) {
        return ctx.getResources().getQuantityString(R.plurals.token_nfts_change, Math.abs(nfts), signed(nfts));
    }

    private static String signed(String category, long n) {
        return (n > 0 ? "+" : "") + TokenLabels.listAmount(category, BigInteger.valueOf(n));
    }

    private static String signedNumber(String category, long n) {
        return (n > 0 ? "+" : "") + TokenLabels.number(category, BigInteger.valueOf(n));
    }

    private static String signed(long n) {
        return (n > 0 ? "+" : "-") + NumberFormat.getIntegerInstance().format(Math.abs(n));
    }
}
