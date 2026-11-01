package cash.bchd.android_neutrino.wallet;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import walletrpc.Api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TokenChangeTest {

    private static Api.TokenData fungible(String category, long amount) {
        return Api.TokenData.newBuilder().setCategory(category).setAmount(amount).build();
    }

    private static Api.TokenData nft(String category, long amount) {
        return Api.TokenData.newBuilder().setCategory(category).setAmount(amount).setHasNft(true).build();
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
                .addCredits(credit(nft("cc", 500)))
                .addCredits(Api.TransactionDetails.Output.newBuilder().setAmount(5000))
                .build();
        assertEquals(Arrays.asList(
                new TokenChange("aa", 0, 1),
                new TokenChange("bb", 1000, 0),
                new TokenChange("cc", 500, 1)), TokenChange.of(tx));
    }

    @Test
    public void takesOffWhatTheWalletSpent() {
        // Sends 250 of 1000 fungible tokens and keeps the rest as change, and sends an NFT.
        Api.TransactionDetails tx = Api.TransactionDetails.newBuilder()
                .addDebits(debit(fungible("bb", 1000)))
                .addDebits(debit(nft("aa", 0)))
                .addDebits(Api.TransactionDetails.Input.newBuilder().setPreviousAmount(10000))
                .addCredits(credit(fungible("bb", 750)))
                .build();
        assertEquals(Arrays.asList(
                new TokenChange("aa", 0, -1),
                new TokenChange("bb", -250, 0)), TokenChange.of(tx));
    }

    @Test
    public void leavesOutWhatTheWalletSentItself() {
        Api.TransactionDetails tx = Api.TransactionDetails.newBuilder()
                .addDebits(debit(fungible("bb", 1000)))
                .addCredits(credit(fungible("bb", 400)))
                .addCredits(credit(fungible("bb", 600)))
                .build();
        assertEquals(Collections.emptyList(), TokenChange.of(tx));
        assertEquals(Collections.emptyList(), TokenChange.of(Api.TransactionDetails.getDefaultInstance()));
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
        Api.TransactionDetails tx = Api.TransactionDetails.newBuilder()
                .addCredits(credit(fungible("bb", 25)))
                .build();
        TransactionData data = Wallet.transactionData(tx, 0, 1761500000L, "");
        assertEquals(Collections.singletonList(new TokenChange("bb", 25, 0)), data.getTokens());
        assertEquals(new TokenChange("bb", 25, 0), TokenChange.find(data.getTokens(), "bb"));
    }
}
