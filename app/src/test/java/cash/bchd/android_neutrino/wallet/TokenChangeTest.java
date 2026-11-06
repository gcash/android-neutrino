package cash.bchd.android_neutrino.wallet;

import com.google.protobuf.ByteString;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import walletrpc.Api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TokenChangeTest {

    private static final List<String> NONE = Collections.emptyList();

    private static Api.TokenData fungible(String category, long amount) {
        return Api.TokenData.newBuilder().setCategory(category).setAmount(amount).build();
    }

    private static Api.TokenData nft(String category, long amount, byte... commitment) {
        return Api.TokenData.newBuilder().setCategory(category).setAmount(amount).setHasNft(true)
                .setCommitment(ByteString.copyFrom(commitment)).build();
    }

    private static Api.TransactionDetails.Output.Builder credit(Api.TokenData token) {
        return Api.TransactionDetails.Output.newBuilder().setAmount(1000).setToken(token);
    }

    private static Api.TransactionDetails.Input.Builder debit(Api.TokenData token) {
        return Api.TransactionDetails.Input.newBuilder().setPreviousAmount(1000).setPreviousToken(token);
    }

    @Test
    public void countsWhatTheWalletReceived() {
        // Fungible tokens over two outputs, an NFT, and an output with both, of another category.
        Api.TransactionDetails tx = Api.TransactionDetails.newBuilder()
                .addCredits(credit(fungible("bb", 600)))
                .addCredits(credit(fungible("bb", 400)))
                .addCredits(credit(nft("aa", 0)))
                .addCredits(credit(nft("cc", 500, (byte) 0x0a)))
                .addCredits(Api.TransactionDetails.Output.newBuilder().setAmount(5000))
                .build();
        assertEquals(Arrays.asList(
                new TokenChange("aa", 0, 1, Collections.singletonList(""), NONE),
                new TokenChange("bb", 1000, 0),
                new TokenChange("cc", 500, 1, Collections.singletonList("0a"), NONE)), TokenChange.of(tx));
    }

    @Test
    public void takesOffWhatTheWalletSpent() {
        // Sends 250 of 1000 fungible tokens and keeps the rest as change, and sends an NFT.
        Api.TransactionDetails tx = Api.TransactionDetails.newBuilder()
                .addDebits(debit(fungible("bb", 1000)))
                .addDebits(debit(nft("aa", 0, (byte) 2)))
                .addDebits(Api.TransactionDetails.Input.newBuilder().setPreviousAmount(10000))
                .addCredits(credit(fungible("bb", 750)))
                .build();
        assertEquals(Arrays.asList(
                new TokenChange("aa", 0, -1, NONE, Collections.singletonList("02")),
                new TokenChange("bb", -250, 0)), TokenChange.of(tx));
    }

    @Test
    public void leavesOutWhatTheWalletSentItself() {
        // Fungible tokens split in two, and an NFT moved to another of the wallet's outputs.
        Api.TransactionDetails tx = Api.TransactionDetails.newBuilder()
                .addDebits(debit(fungible("bb", 1000)))
                .addDebits(debit(nft("aa", 0, (byte) 1)))
                .addCredits(credit(fungible("bb", 400)))
                .addCredits(credit(fungible("bb", 600)))
                .addCredits(credit(nft("aa", 0, (byte) 1)))
                .build();
        assertEquals(Collections.emptyList(), TokenChange.of(tx));
        assertEquals(Collections.emptyList(), TokenChange.of(Api.TransactionDetails.getDefaultInstance()));
    }

    @Test
    public void keepsTheNftsThatCameAndWent() {
        // A Minting NFT without a commitment, which comes back, mints two NFTs to the wallet, and
        // the wallet sends away its NFT with the commitment 03.
        Api.TransactionDetails tx = Api.TransactionDetails.newBuilder()
                .addDebits(debit(nft("aa", 0)))
                .addDebits(debit(nft("aa", 0, (byte) 3)))
                .addCredits(credit(nft("aa", 0)))
                .addCredits(credit(nft("aa", 0, (byte) 1)))
                .addCredits(credit(nft("aa", 0, (byte) 2)))
                .build();
        TokenChange change = TokenChange.of(tx).get(0);
        assertEquals(1, change.nfts);
        assertEquals(Arrays.asList("01", "02"), change.nftsReceived());
        assertEquals(Collections.singletonList("03"), change.nftsSent());
    }

    @Test
    public void receivesOnlyWhatItTakesNothingOf() {
        assertTrue(new TokenChange("aa", 5, 0).received());
        assertTrue(new TokenChange("aa", 0, 1).received());
        assertFalse(new TokenChange("aa", -5, 0).received());
        assertFalse(new TokenChange("aa", 5, -1).received());
    }

    @Test
    public void keepsTheChangesInTheTransactionsEntry() {
        // A transaction that spends 2000 of the wallet's sats, and pays it 25 fungible tokens on
        // 1000 sats, and 3000 sats.
        Api.TransactionDetails tx = Api.TransactionDetails.newBuilder()
                .addDebits(Api.TransactionDetails.Input.newBuilder().setPreviousAmount(2000))
                .addCredits(credit(fungible("bb", 25)))
                .addCredits(Api.TransactionDetails.Output.newBuilder().setAmount(3000))
                .build();
        TransactionData data = Wallet.transactionData(tx, 0, 1761500000L, "");
        assertEquals(Collections.singletonList(new TokenChange("bb", 25, 0)), data.getTokens());
        assertEquals(new TokenChange("bb", 25, 0), TokenChange.find(data.getTokens(), "bb"));
        // The BCH on the token's output isn't spendable.
        assertEquals(2000, data.getAmount());
        assertEquals(1000, data.getSpendableAmount());
    }

    @Test
    public void onlyNftIsTheOneNftAChangeIsAllAbout() {
        List<String> none = Collections.emptyList();
        assertEquals("01", new TokenChange("c", 0, 1, Collections.singletonList("01"), none).onlyNft());
        assertEquals("02", new TokenChange("c", 0, -1, none, Collections.singletonList("02")).onlyNft());
        assertEquals(null, new TokenChange("c", 5, 1, Collections.singletonList("01"), none).onlyNft());
        assertEquals(null, new TokenChange("c", 0, 2, Arrays.asList("01", "02"), none).onlyNft());
        assertEquals(null, new TokenChange("c", 0, 1).onlyNft());
    }
}
