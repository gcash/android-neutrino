package cash.bchd.android_neutrino.wallet;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import walletrpc.Api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TokenBalanceTest {

    // balance returns a category's balance, with as many NFTs as nfts, and an output that
    // carries each of the BCH amounts given.
    private static Api.TokenBalancesResponse.Balance.Builder balance(String category, long amount, int nfts,
                                                                    long... outputSatoshis) {
        Api.TokenBalancesResponse.Balance.Builder balance = Api.TokenBalancesResponse.Balance.newBuilder()
                .setCategory(category).setAmount(amount);
        for (int i = 0; i < nfts; i++) {
            balance.addNfts(Api.TokenBalancesResponse.NFT.getDefaultInstance());
        }
        for (long satoshis : outputSatoshis) {
            balance.addOutputs(Api.TokenBalancesResponse.Output.newBuilder().setSatoshis(satoshis));
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
                new TokenBalance("aa", 5, 0, 0),
                new TokenBalance("bb", 7, 1, 0),
                new TokenBalance("0a", 0, 1, 0),
                new TokenBalance("cc", 0, 2, 0)), TokenBalance.of(response));
        assertEquals(Collections.emptyList(), TokenBalance.of(Api.TokenBalancesResponse.getDefaultInstance()));
    }

    @Test
    public void countsOnlyTheBchAboveTheFloorOfEachOutput() {
        // Outputs with the least the network relays, the 1000 sats wallets attach, a little more,
        // and 0.002 BCH.
        Api.TokenBalancesResponse response = Api.TokenBalancesResponse.newBuilder()
                .addBalances(balance("aa", 1000000, 0, 651, 1000, 1035, 200000))
                .addBalances(balance("bb", 7, 0, 651))
                .build();
        List<TokenBalance> tokens = TokenBalance.of(response);
        assertEquals(35 + 199000, TokenBalance.find(tokens, "aa").satoshisAboveFloor);
        assertEquals(0, TokenBalance.find(tokens, "bb").satoshisAboveFloor);
        assertNull(TokenBalance.find(tokens, "cc"));
    }

    @Test
    public void readsWhatItWrote() {
        // -1 is the largest uint64, which the long holds.
        List<TokenBalance> tokens = Arrays.asList(
                new TokenBalance("da3598d398c438a8d579cbe4139363447331b631d1cc01c3d02dbf9105e3aecf", 1000000, 0, 0),
                new TokenBalance("b1d29053024c4ee908f7cbf170677e8a13928bbe5cc909fcb546e404ec7848dd", -1, 3, 199035));
        assertEquals(tokens, TokenBalance.fromJson(TokenBalance.toJson(tokens)));
        assertEquals(Collections.emptyList(), TokenBalance.fromJson(TokenBalance.toJson(Collections.emptyList())));
    }

    @Test
    public void readsWhatItWroteBeforeItKeptTheBch() {
        assertEquals(Collections.singletonList(new TokenBalance("aa", 5, 1, 0)),
                TokenBalance.fromJson("[{\"category\":\"aa\",\"amount\":\"5\",\"nfts\":1}]"));
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
