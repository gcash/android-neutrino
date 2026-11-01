package cash.bchd.android_neutrino.wallet;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import walletrpc.Api;

import static org.junit.Assert.assertEquals;

public class TokenBalanceTest {

    private static Api.TokenBalancesResponse.Balance.Builder balance(String category, long amount, int nfts) {
        Api.TokenBalancesResponse.Balance.Builder balance = Api.TokenBalancesResponse.Balance.newBuilder()
                .setCategory(category).setAmount(amount);
        for (int i = 0; i < nfts; i++) {
            balance.addNfts(Api.TokenBalancesResponse.NFT.getDefaultInstance());
        }
        return balance;
    }

    @Test
    public void listsFungibleTokensFirst() {
        Api.TokenBalancesResponse response = Api.TokenBalancesResponse.newBuilder()
                .addBalances(balance("cc", 0, 2))
                .addBalances(balance("bb", 7, 1))
                .addBalances(balance("0a", 0, 1))
                .addBalances(balance("aa", 5, 0))
                .build();

        assertEquals(Arrays.asList(
                new TokenBalance("aa", 5, 0),
                new TokenBalance("bb", 7, 1),
                new TokenBalance("0a", 0, 1),
                new TokenBalance("cc", 0, 2)), TokenBalance.of(response));
        assertEquals(Collections.emptyList(), TokenBalance.of(Api.TokenBalancesResponse.getDefaultInstance()));
    }

    @Test
    public void readsWhatItWrote() {
        // -1 is the largest uint64, which the long holds.
        List<TokenBalance> tokens = Arrays.asList(
                new TokenBalance("da3598d398c438a8d579cbe4139363447331b631d1cc01c3d02dbf9105e3aecf", 1000000, 0),
                new TokenBalance("b1d29053024c4ee908f7cbf170677e8a13928bbe5cc909fcb546e404ec7848dd", -1, 3));
        assertEquals(tokens, TokenBalance.fromJson(TokenBalance.toJson(tokens)));
        assertEquals(Collections.emptyList(), TokenBalance.fromJson(TokenBalance.toJson(Collections.emptyList())));
    }

    @Test
    public void readsNoTokensFromAnythingElse() {
        assertEquals(Collections.emptyList(), TokenBalance.fromJson(""));
        assertEquals(Collections.emptyList(), TokenBalance.fromJson("{}"));
        assertEquals(Collections.emptyList(), TokenBalance.fromJson("[{\"category\":\"aa\",\"amount\":\"x\",\"nfts\":0}]"));
    }

    @Test
    public void shortensTokenIds() {
        assertEquals("da3598…aecf",
                TokenBalance.shortId("da3598d398c438a8d579cbe4139363447331b631d1cc01c3d02dbf9105e3aecf"));
        assertEquals("abcd", TokenBalance.shortId("abcd"));
    }
}
