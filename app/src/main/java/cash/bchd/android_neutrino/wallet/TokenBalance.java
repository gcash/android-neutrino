package cash.bchd.android_neutrino.wallet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import walletrpc.Api;

/**
 * TokenBalance is what the wallet holds of a token category, as the main screen's Tokens page
 * and the token's screen show it: the fungible amount, how many NFTs, and the BCH its outputs
 * carry above the floor.
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
    public final int nfts;
    // The BCH, in satoshis, that the category's outputs carry above FLOOR each.
    public final long satoshisAboveFloor;

    public TokenBalance(String category, long amount, int nfts, long satoshisAboveFloor) {
        this.category = category;
        this.amount = amount;
        this.nfts = nfts;
        this.satoshisAboveFloor = satoshisAboveFloor;
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
            for (Api.TokenBalancesResponse.Output output : balance.getOutputsList()) {
                aboveFloor += Math.max(0, output.getSatoshis() - FLOOR);
            }
            TokenBalance token = new TokenBalance(balance.getCategory(), balance.getAmount(),
                    balance.getNftsCount(), aboveFloor);
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
                array.put(new JSONObject()
                        .put("category", token.category)
                        .put("amount", Long.toUnsignedString(token.amount))
                        .put("nfts", token.nfts)
                        .put("aboveFloor", token.satoshisAboveFloor));
            }
        } catch (JSONException e) {
            // put throws only for a null name, or a number that isn't finite.
            throw new IllegalStateException(e);
        }
        return array.toString();
    }

    /**
     * fromJson reads what toJson wrote, also before it wrote the BCH above the floor, and no
     * token balances from anything else.
     */
    public static List<TokenBalance> fromJson(String json) {
        List<TokenBalance> tokens = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject token = array.getJSONObject(i);
                tokens.add(new TokenBalance(token.getString("category"),
                        Long.parseUnsignedLong(token.getString("amount")), token.getInt("nfts"),
                        token.optLong("aboveFloor", 0)));
            }
        } catch (JSONException | NumberFormatException e) {
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
                && satoshisAboveFloor == other.satoshisAboveFloor;
    }

    @Override
    public int hashCode() {
        return Objects.hash(category, amount, nfts, satoshisAboveFloor);
    }

    @Override
    public String toString() {
        return category + " " + Long.toUnsignedString(amount) + " " + nfts + " " + satoshisAboveFloor;
    }
}
