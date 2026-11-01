package cash.bchd.android_neutrino.wallet;

import java.util.Comparator;
import java.util.Objects;

/**
 * TokenNft is one of the NFTs of a token category that the wallet holds: what it can do, its
 * commitment, the output that holds it, and the BCH that output carries.
 */
public final class TokenNft {

    /**
     * Capability is what an NFT can do besides being held: nothing, have its commitment changed
     * when it's sent, or mint new NFTs of its category.
     */
    public enum Capability {
        NONE,
        MUTABLE,
        MINTING
    }

    /**
     * ORDER sorts NFTs by commitment, so that a numbered collection comes out in order, an NFT
     * without one first, then by the output that holds it.
     */
    static final Comparator<TokenNft> ORDER = Comparator.<TokenNft, String>comparing(nft -> nft.commitment)
            .thenComparing(nft -> nft.txid).thenComparingInt(nft -> nft.index);

    public final Capability capability;
    // The commitment, in hex, empty for none.
    public final String commitment;
    // The output that holds it: the ID of the transaction it came in, and the output's index.
    public final String txid;
    public final int index;
    // The BCH, in satoshis, that its output carries.
    public final long satoshis;

    public TokenNft(Capability capability, String commitment, String txid, int index, long satoshis) {
        this.capability = capability;
        this.commitment = commitment;
        this.txid = txid;
        this.index = index;
        this.satoshis = satoshis;
    }

    /**
     * satoshisAboveFloor returns the BCH, in satoshis, that its output carries above the floor.
     */
    public long satoshisAboveFloor() {
        return Math.max(0, satoshis - TokenBalance.FLOOR);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TokenNft)) {
            return false;
        }
        TokenNft other = (TokenNft) o;
        return capability == other.capability && commitment.equals(other.commitment) && txid.equals(other.txid)
                && index == other.index && satoshis == other.satoshis;
    }

    @Override
    public int hashCode() {
        return Objects.hash(capability, commitment, txid, index, satoshis);
    }

    @Override
    public String toString() {
        return capability + " " + commitment + " " + txid + ":" + index + " " + satoshis;
    }
}
