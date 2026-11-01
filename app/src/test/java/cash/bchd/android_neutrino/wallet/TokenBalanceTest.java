package cash.bchd.android_neutrino.wallet;

import com.google.protobuf.ByteString;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import walletrpc.Api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TokenBalanceTest {

    // A transaction's hash in the byte order of the wire, and its ID, which has the bytes the
    // other way around.
    private static final ByteString HASH = ByteString.copyFrom(new byte[]{1, 2, 3});
    private static final String TXID = "030201";

    private static final List<TokenNft> NONE = Collections.emptyList();

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

    private static TokenNft unknown() {
        return new TokenNft(TokenNft.Capability.NONE, "", "", 0, 0);
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
                new TokenBalance("aa", 5, 0, 0, NONE),
                new TokenBalance("bb", 7, 1, 0, Collections.singletonList(unknown())),
                new TokenBalance("0a", 0, 1, 0, Collections.singletonList(unknown())),
                new TokenBalance("cc", 0, 2, 0, Arrays.asList(unknown(), unknown()))), TokenBalance.of(response));
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
    public void listsTheCollectionByCommitment() {
        // A Minting NFT without a commitment, a mutable one, and one with a commitment that
        // starts like the mutable one's, each in an output of its own, which carries the BCH
        // given.
        Api.TokenBalancesResponse.Balance.Builder balance = Api.TokenBalancesResponse.Balance.newBuilder()
                .setCategory("aa");
        Object[][] nfts = {
                {Api.TokenData.Capability.MUTABLE, new byte[]{2}, 1, 1000L},
                {Api.TokenData.Capability.NONE, new byte[]{2, 0}, 2, 1035L},
                {Api.TokenData.Capability.MINTING, new byte[0], 0, 5000L},
        };
        for (Object[] nft : nfts) {
            ByteString commitment = ByteString.copyFrom((byte[]) nft[1]);
            int index = (Integer) nft[2];
            balance.addNfts(Api.TokenBalancesResponse.NFT.newBuilder().setTransactionHash(HASH)
                    .setOutputIndex(index).setCapability((Api.TokenData.Capability) nft[0])
                    .setCommitment(commitment));
            balance.addOutputs(Api.TokenBalancesResponse.Output.newBuilder().setTransactionHash(HASH)
                    .setOutputIndex(index).setSatoshis((Long) nft[3]));
        }
        TokenBalance token = TokenBalance.of(Api.TokenBalancesResponse.newBuilder().addBalances(balance).build()).get(0);

        assertEquals(3, token.nfts);
        assertEquals(Arrays.asList(
                new TokenNft(TokenNft.Capability.MINTING, "", TXID, 0, 5000),
                new TokenNft(TokenNft.Capability.MUTABLE, "02", TXID, 1, 1000),
                new TokenNft(TokenNft.Capability.NONE, "0200", TXID, 2, 1035)), token.collection);
        assertEquals(35, token.findNft(TXID, 2).satoshisAboveFloor());
        assertNull(token.findNft(TXID, 3));
    }

    @Test
    public void readsWhatItWrote() {
        // -1 is the largest uint64, which the long holds.
        List<TokenBalance> tokens = Arrays.asList(
                new TokenBalance("da3598d398c438a8d579cbe4139363447331b631d1cc01c3d02dbf9105e3aecf", 1000000, 0, 0, NONE),
                new TokenBalance("b1d29053024c4ee908f7cbf170677e8a13928bbe5cc909fcb546e404ec7848dd", -1, 2, 199035,
                        Arrays.asList(new TokenNft(TokenNft.Capability.MINTING, "", TXID, 0, 1000),
                                new TokenNft(TokenNft.Capability.MUTABLE, "aa", TXID, 3, 200035))));
        assertEquals(tokens, TokenBalance.fromJson(TokenBalance.toJson(tokens)));
        assertEquals(Collections.emptyList(), TokenBalance.fromJson(TokenBalance.toJson(Collections.emptyList())));
    }

    @Test
    public void readsWhatItWroteBeforeItKeptTheBchAndTheCollection() {
        assertEquals(Collections.singletonList(new TokenBalance("aa", 5, 1, 0, NONE)),
                TokenBalance.fromJson("[{\"category\":\"aa\",\"amount\":\"5\",\"nfts\":1}]"));
    }

    @Test
    public void readsNoTokensFromAnythingElse() {
        assertEquals(Collections.emptyList(), TokenBalance.fromJson(""));
        assertEquals(Collections.emptyList(), TokenBalance.fromJson("{}"));
        assertEquals(Collections.emptyList(), TokenBalance.fromJson("[{\"category\":\"aa\",\"amount\":\"x\",\"nfts\":0}]"));
        assertEquals(Collections.emptyList(), TokenBalance.fromJson("[{\"category\":\"aa\",\"amount\":\"5\",\"nfts\":1,"
                + "\"collection\":[{\"capability\":\"SPECIAL\",\"commitment\":\"\",\"txid\":\"\",\"index\":0,\"satoshis\":0}]}]"));
    }

    @Test
    public void shortensTokenIds() {
        assertEquals("da3598…aecf",
                TokenBalance.shortId("da3598d398c438a8d579cbe4139363447331b631d1cc01c3d02dbf9105e3aecf"));
        assertEquals("abcd", TokenBalance.shortId("abcd"));
    }
}
