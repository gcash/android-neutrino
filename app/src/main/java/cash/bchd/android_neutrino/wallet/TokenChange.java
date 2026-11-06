package cash.bchd.android_neutrino.wallet;

import com.google.common.io.BaseEncoding;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import walletrpc.Api;

/**
 * TokenChange is how a transaction changed what the wallet holds of a token category: its
 * fungible amount and its number of NFTs, each more or less, and which NFTs came and went, as
 * TransactionData keeps it.
 */
public final class TokenChange implements Serializable {

    // The same as before the class kept the NFTs' commitments, as it only has more fields since.
    private static final long serialVersionUID = 1L;

    public final String category;
    public final long amount;
    public final int nfts;
    // The commitments, in hex, of the NFTs that came to the wallet and that it sent, but for those
    // it sent itself, or null in a change saved before the app kept them.
    private final ArrayList<String> nftsReceived;
    private final ArrayList<String> nftsSent;

    public TokenChange(String category, long amount, int nfts) {
        this(category, amount, nfts, Collections.emptyList(), Collections.emptyList());
    }

    public TokenChange(String category, long amount, int nfts, List<String> nftsReceived, List<String> nftsSent) {
        this.category = category;
        this.amount = amount;
        this.nfts = nfts;
        this.nftsReceived = new ArrayList<>(nftsReceived);
        this.nftsSent = new ArrayList<>(nftsSent);
    }

    /**
     * of returns how the transaction changed what the wallet holds of each token category, by
     * token ID: what its outputs to the wallet hold, less what the wallet's outputs it spent held.
     * It leaves out the categories that it didn't change, as when the wallet sent tokens to
     * itself.
     */
    static List<TokenChange> of(Api.TransactionDetails tx) {
        Map<String, Change> changes = new TreeMap<>();
        for (Api.TransactionDetails.Output output : tx.getCreditsList()) {
            if (output.hasToken()) {
                change(changes, output.getToken()).add(output.getToken(), 1);
            }
        }
        for (Api.TransactionDetails.Input input : tx.getDebitsList()) {
            if (input.hasPreviousToken()) {
                change(changes, input.getPreviousToken()).add(input.getPreviousToken(), -1);
            }
        }
        List<TokenChange> list = new ArrayList<>();
        for (Map.Entry<String, Change> entry : changes.entrySet()) {
            Change change = entry.getValue();
            // An NFT that went out and came back, with the same commitment, stayed in the wallet.
            for (String commitment : new ArrayList<>(change.received)) {
                if (change.sent.remove(commitment)) {
                    change.received.remove(commitment);
                }
            }
            // NFTs that were swapped, or a commitment that changed, come to no NFTs more or fewer.
            int nfts = change.received.size() - change.sent.size();
            if (change.amount != 0 || !change.received.isEmpty() || !change.sent.isEmpty()) {
                list.add(new TokenChange(entry.getKey(), change.amount, nfts, change.received, change.sent));
            }
        }
        return list;
    }

    private static Change change(Map<String, Change> changes, Api.TokenData token) {
        return changes.computeIfAbsent(token.getCategory(), category -> new Change());
    }

    // Change sums up a category's tokens in and out of the wallet.
    private static final class Change {
        long amount;
        final List<String> received = new ArrayList<>();
        final List<String> sent = new ArrayList<>();

        void add(Api.TokenData token, int sign) {
            amount += sign * token.getAmount();
            if (token.getHasNft()) {
                String commitment = BaseEncoding.base16().lowerCase().encode(token.getCommitment().toByteArray());
                (sign > 0 ? received : sent).add(commitment);
            }
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
     * onlyNft returns the commitment of the one NFT the change is all about, or null if it's about
     * anything else.
     */
    public String onlyNft() {
        List<String> received = nftsReceived();
        List<String> sent = nftsSent();
        if (amount != 0 || received.size() + sent.size() != 1) {
            return null;
        }
        return received.isEmpty() ? sent.get(0) : received.get(0);
    }

    /**
     * nftsReceived returns the commitments, in hex, of the NFTs that came to the wallet.
     */
    public List<String> nftsReceived() {
        return nftsReceived != null ? Collections.unmodifiableList(nftsReceived) : Collections.emptyList();
    }

    /**
     * nftsSent returns the commitments, in hex, of the NFTs that the wallet sent.
     */
    public List<String> nftsSent() {
        return nftsSent != null ? Collections.unmodifiableList(nftsSent) : Collections.emptyList();
    }

    /**
     * received returns whether the wallet holds more of the category after the transaction, and
     * no less of either its fungible tokens or its NFTs.
     */
    public boolean swapped() {
        return amount == 0 && nfts == 0;
    }

    public boolean received() {
        return amount >= 0 && nfts >= 0;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TokenChange)) {
            return false;
        }
        TokenChange other = (TokenChange) o;
        return category.equals(other.category) && amount == other.amount && nfts == other.nfts
                && nftsReceived().equals(other.nftsReceived()) && nftsSent().equals(other.nftsSent());
    }

    @Override
    public int hashCode() {
        return Objects.hash(category, amount, nfts, nftsReceived(), nftsSent());
    }

    @Override
    public String toString() {
        return category + " " + amount + " " + nfts + " +" + nftsReceived() + " -" + nftsSent();
    }
}
