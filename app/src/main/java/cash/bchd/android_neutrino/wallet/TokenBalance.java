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
 * shows it: the fungible amount, and how many NFTs.
 */
public final class TokenBalance {

    // The category, in hex in the byte order of transaction IDs, as token IDs are written.
    public final String category;
    // The fungible amount, a uint64 in a long, as the API's Java code has it.
    public final long amount;
    public final int nfts;

    public TokenBalance(String category, long amount, int nfts) {
        this.category = category;
        this.amount = amount;
        this.nfts = nfts;
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
            TokenBalance token = new TokenBalance(balance.getCategory(), balance.getAmount(), balance.getNftsCount());
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
                        .put("nfts", token.nfts));
            }
        } catch (JSONException e) {
            // put throws only for a null name, or a number that isn't finite.
            throw new IllegalStateException(e);
        }
        return array.toString();
    }

    /**
     * fromJson reads what toJson wrote, and no token balances from anything else.
     */
    public static List<TokenBalance> fromJson(String json) {
        List<TokenBalance> tokens = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject token = array.getJSONObject(i);
                tokens.add(new TokenBalance(token.getString("category"),
                        Long.parseUnsignedLong(token.getString("amount")), token.getInt("nfts")));
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
        return category.equals(other.category) && amount == other.amount && nfts == other.nfts;
    }

    @Override
    public int hashCode() {
        return Objects.hash(category, amount, nfts);
    }

    @Override
    public String toString() {
        return category + " " + Long.toUnsignedString(amount) + " " + nfts;
    }
}
