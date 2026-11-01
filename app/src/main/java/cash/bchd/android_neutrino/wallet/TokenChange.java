package cash.bchd.android_neutrino.wallet;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import walletrpc.Api;

/**
 * TokenChange is how a transaction changed what the wallet holds of a token category: its
 * fungible amount and its number of NFTs, each more or less, as TransactionData keeps it.
 */
public final class TokenChange implements Serializable {

    private static final long serialVersionUID = 1L;

    public final String category;
    public final long amount;
    public final int nfts;

    public TokenChange(String category, long amount, int nfts) {
        this.category = category;
        this.amount = amount;
        this.nfts = nfts;
    }

    /**
     * of returns how the transaction changed what the wallet holds of each token category, by
     * token ID: what its outputs to the wallet hold, less what the wallet's outputs it spent held.
     * It leaves out the categories that it didn't change, as when the wallet sent tokens to
     * itself.
     */
    static List<TokenChange> of(Api.TransactionDetails tx) {
        Map<String, long[]> changes = new TreeMap<>();
        for (Api.TransactionDetails.Output output : tx.getCreditsList()) {
            if (output.hasToken()) {
                add(changes, output.getToken(), 1);
            }
        }
        for (Api.TransactionDetails.Input input : tx.getDebitsList()) {
            if (input.hasPreviousToken()) {
                add(changes, input.getPreviousToken(), -1);
            }
        }
        List<TokenChange> list = new ArrayList<>();
        for (Map.Entry<String, long[]> change : changes.entrySet()) {
            long[] by = change.getValue();
            if (by[0] != 0 || by[1] != 0) {
                list.add(new TokenChange(change.getKey(), by[0], (int) by[1]));
            }
        }
        return list;
    }

    private static void add(Map<String, long[]> changes, Api.TokenData token, int sign) {
        long[] by = changes.computeIfAbsent(token.getCategory(), category -> new long[2]);
        by[0] += sign * token.getAmount();
        if (token.getHasNft()) {
            by[1] += sign;
        }
    }

    /**
     * find returns the change of the category among the changes, or null if there is none.
     */
    public static TokenChange find(List<TokenChange> changes, String category) {
        for (TokenChange change : changes) {
            if (change.category.equals(category)) {
                return change;
            }
        }
        return null;
    }

    /**
     * received returns whether the wallet holds more of the category after the transaction, and
     * no less of either its fungible tokens or its NFTs.
     */
    public boolean received() {
        return amount >= 0 && nfts >= 0;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TokenChange)) {
            return false;
        }
        TokenChange other = (TokenChange) o;
        return category.equals(other.category) && amount == other.amount && nfts == other.nfts;
    }

    @Override
    public int hashCode() {
        return Objects.hash(category, amount, nfts);
    }

    @Override
    public String toString() {
        return category + " " + amount + " " + nfts;
    }
}
