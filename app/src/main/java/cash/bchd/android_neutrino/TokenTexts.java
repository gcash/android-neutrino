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
        String nfts = change.nfts == 0 ? null : nfts(ctx, change.nfts);
        if (amount == null) {
            return nfts;
        }
        return nfts == null ? amount : ctx.getString(R.string.token_amount_and_nfts, amount, nfts);
    }

    /**
     * details says, a line for each, how the transaction changed the token: its fungible amount,
     * and each NFT that came or went, with its commitment.
     */
    static String details(Context ctx, TokenChange change) {
        List<String> lines = new ArrayList<>();
        if (change.amount != 0) {
            lines.add(signed(change.category, change.amount));
        }
        for (String commitment : change.nftsReceived()) {
            lines.add(commitment.isEmpty() ? ctx.getString(R.string.nft_in)
                    : ctx.getString(R.string.nft_in_with, NftAdapter.shortCommitment(commitment)));
        }
        for (String commitment : change.nftsSent()) {
            lines.add(commitment.isEmpty() ? ctx.getString(R.string.nft_out)
                    : ctx.getString(R.string.nft_out_with, NftAdapter.shortCommitment(commitment)));
        }
        // A change saved before the app kept the NFTs' commitments has how many only.
        if (change.nfts != 0 && change.nftsReceived().isEmpty() && change.nftsSent().isEmpty()) {
            lines.add(nfts(ctx, change.nfts));
        }
        return TextUtils.join("\n", lines);
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
