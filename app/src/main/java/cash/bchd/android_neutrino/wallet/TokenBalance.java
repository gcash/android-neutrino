package cash.bchd.android_neutrino.wallet;

import com.google.common.io.BaseEncoding;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import walletrpc.Api;

/**
 * TokenBalance is what the wallet holds of a token category, as the main screen's Tokens page
 * and the token's screens show it: the fungible amount, its NFTs, and the BCH its outputs carry
 * above the floor.
 */
public final class TokenBalance {

    /**
     * FLOOR is the BCH, in satoshis, that the app leaves out of what a token output carries, as
     * mainnet-js, which Cashonize is built on, attaches 1000 sats to each token output: an output
     * shows only the BCH it carries above that.
     */
    public static final long FLOOR = 1000;

    // The category, in hex in the byte order of transaction IDs, as token IDs are written.
    public final String category;
    // The fungible amount, a uint64 in a long, as the API's Java code has it.
    public final long amount;
    // How many NFTs, which the collection lists, but for balances kept before it did.
    public final int nfts;
    // The BCH, in satoshis, that the category's outputs carry above FLOOR each.
    public final long satoshisAboveFloor;
    // The NFTs, in TokenNft.ORDER.
    public final List<TokenNft> collection;

    public TokenBalance(String category, long amount, int nfts, long satoshisAboveFloor, List<TokenNft> collection) {
        this.category = category;
        this.amount = amount;
        this.nfts = nfts;
        this.satoshisAboveFloor = satoshisAboveFloor;
        this.collection = Collections.unmodifiableList(new ArrayList<>(collection));
    }

    /**
     * of reads the wallet's token balances in the order the Tokens page lists them: the
     * categories with fungible tokens first, with NFTs or not, then those with NFTs only, each
     * by token ID.
     */
    static List<TokenBalance> of(Api.TokenBalancesResponse response) {
        List<TokenBalance> fungible = new ArrayList<>();
        List<TokenBalance> nftsOnly = new ArrayList<>();
        for (Api.TokenBalancesResponse.Balance balance : response.getBalancesList()) {
            long aboveFloor = 0;
            // The BCH of each output, by outpoint, for the NFT that it holds.
            Map<String, Long> satoshis = new HashMap<>();
            for (Api.TokenBalancesResponse.Output output : balance.getOutputsList()) {
                aboveFloor += Math.max(0, output.getSatoshis() - FLOOR);
                satoshis.put(Wallet.txidToString(output.getTransactionHash()) + ":" + output.getOutputIndex(),
                        output.getSatoshis());
            }
            List<TokenNft> collection = new ArrayList<>();
            for (Api.TokenBalancesResponse.NFT nft : balance.getNftsList()) {
                String txid = Wallet.txidToString(nft.getTransactionHash());
                Long held = satoshis.get(txid + ":" + nft.getOutputIndex());
                collection.add(new TokenNft(capability(nft.getCapability()),
                        BaseEncoding.base16().lowerCase().encode(nft.getCommitment().toByteArray()),
                        txid, nft.getOutputIndex(), held != null ? held : 0));
            }
            collection.sort(TokenNft.ORDER);
            TokenBalance token = new TokenBalance(balance.getCategory(), balance.getAmount(),
                    balance.getNftsCount(), aboveFloor, collection);
            if (token.amount != 0) {
                fungible.add(token);
            } else {
                nftsOnly.add(token);
            }
        }
        Comparator<TokenBalance> byId = Comparator.comparing(token -> token.category);
        fungible.sort(byId);
        nftsOnly.sort(byId);
        fungible.addAll(nftsOnly);
        return fungible;
    }

    private static TokenNft.Capability capability(Api.TokenData.Capability capability) {
        switch (capability) {
            case MUTABLE:
                return TokenNft.Capability.MUTABLE;
            case MINTING:
                return TokenNft.Capability.MINTING;
            default:
                return TokenNft.Capability.NONE;
        }
    }

    /**
     * find returns the balance of the category among the balances, or null if there is none.
     */
    public static TokenBalance find(List<TokenBalance> tokens, String category) {
        for (TokenBalance token : tokens) {
            if (token.category.equals(category)) {
                return token;
            }
        }
        return null;
    }

    /**
     * findNft returns the NFT that the output holds, or null if the collection has none there.
     */
    public TokenNft findNft(String txid, int index) {
        for (TokenNft nft : collection) {
            if (nft.txid.equals(txid) && nft.index == index) {
                return nft;
            }
        }
        return null;
    }

    /**
     * shortId shortens a token ID to its first six and last four digits, as the Tokens page
     * shows it.
     */
    public static String shortId(String category) {
        if (category.length() <= 10) {
            return category;
        }
        return category.substring(0, 6) + "…" + category.substring(category.length() - 4);
    }

    /**
     * toJson writes the token balances for the main screen to show when it opens again, before
     * the wallet has answered, as it shows the last balance.
     */
    public static String toJson(List<TokenBalance> tokens) {
        JSONArray array = new JSONArray();
        try {
            for (TokenBalance token : tokens) {
                JSONArray collection = new JSONArray();
                for (TokenNft nft : token.collection) {
                    collection.put(new JSONObject()
                            .put("capability", nft.capability.name())
                            .put("commitment", nft.commitment)
                            .put("txid", nft.txid)
                            .put("index", nft.index)
                            .put("satoshis", nft.satoshis));
                }
                array.put(new JSONObject()
                        .put("category", token.category)
                        .put("amount", Long.toUnsignedString(token.amount))
                        .put("nfts", token.nfts)
                        .put("aboveFloor", token.satoshisAboveFloor)
                        .put("collection", collection));
            }
        } catch (JSONException e) {
            // put throws only for a null name, or a number that isn't finite.
            throw new IllegalStateException(e);
        }
        return array.toString();
    }

    /**
     * fromJson reads what toJson wrote, also before it wrote the BCH above the floor and the
     * collection, and no token balances from anything else.
     */
    public static List<TokenBalance> fromJson(String json) {
        List<TokenBalance> tokens = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject token = array.getJSONObject(i);
                List<TokenNft> collection = new ArrayList<>();
                JSONArray nfts = token.optJSONArray("collection");
                for (int j = 0; nfts != null && j < nfts.length(); j++) {
                    JSONObject nft = nfts.getJSONObject(j);
                    collection.add(new TokenNft(TokenNft.Capability.valueOf(nft.getString("capability")),
                            nft.getString("commitment"), nft.getString("txid"), nft.getInt("index"),
                            nft.getLong("satoshis")));
                }
                tokens.add(new TokenBalance(token.getString("category"),
                        Long.parseUnsignedLong(token.getString("amount")), token.getInt("nfts"),
                        token.optLong("aboveFloor", 0), collection));
            }
        } catch (JSONException | IllegalArgumentException e) {
            return new ArrayList<>();
        }
        return tokens;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TokenBalance)) {
            return false;
        }
        TokenBalance other = (TokenBalance) o;
        return category.equals(other.category) && amount == other.amount && nfts == other.nfts
                && satoshisAboveFloor == other.satoshisAboveFloor && collection.equals(other.collection);
    }

    @Override
    public int hashCode() {
        return Objects.hash(category, amount, nfts, satoshisAboveFloor, collection);
    }

    @Override
    public String toString() {
        return category + " " + Long.toUnsignedString(amount) + " " + nfts + " " + satoshisAboveFloor + " " + collection;
    }
}
