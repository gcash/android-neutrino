package cash.bchd.android_neutrino.wallet;

import org.junit.Test;

import io.grpc.Status;
import walletrpc.Api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WalletTest {

    // Addresses from the CashAddr specification's examples.
    private static final String PAYEE = "qpm2qsznhks23z7629mms6s4cwef74vcwvy22gdx6a";
    private static final String SECOND_PAYEE = "qr95sy3j9xwd2ap32xkykttr4cvcu7as4y0qverfuy";
    private static final String SCRIPT_PAYEE = "ppm2qsznhks23z7629mms6s4cwef74vcwvn0h829pq";

    // sent returns a transaction that spends 100000 satoshis of the wallet's, pays 60000 back to it
    // as change, and has other outputs that pay the addresses, where "" is one that pays none.
    private static Api.TransactionDetails sent(String... addresses) {
        Api.TransactionDetails.Builder tx = Api.TransactionDetails.newBuilder()
                .addDebits(Api.TransactionDetails.Input.newBuilder().setPreviousAmount(100000))
                .addCredits(Api.TransactionDetails.Output.newBuilder().setAmount(60000).setInternal(true));
        for (String address : addresses) {
            tx.addOtherOutputs(Api.TransactionDetails.OtherOutput.newBuilder().setAddress(address));
        }
        return tx.build();
    }

    @Test
    public void namesWhomASentTransactionPaid() {
        TransactionData tx = Wallet.transactionData(sent(PAYEE), 812345, 1761500000L, "lunch");
        assertFalse(tx.getIncoming());
        assertEquals(-40000, tx.getAmount());
        assertEquals("lunch", tx.getMemo());
        assertEquals(812345, tx.getHeight());
        assertEquals(1761500000L, tx.getTimestamp());
        assertEquals(PAYEE, tx.getToAddress());
        assertEquals(TransactionData.SentTo.ADDRESS, tx.getSentTo());
    }

    @Test
    public void countsEachMoreAddressOnce() {
        // OP_RETURN data first, and a second payment to the payee among two more addresses.
        TransactionData tx = Wallet.transactionData(sent("", PAYEE, SECOND_PAYEE, PAYEE, SCRIPT_PAYEE), 10, 100, "");
        assertEquals(PAYEE, tx.getToAddress());
        assertEquals(2, tx.getMoreRecipients());
        assertEquals(TransactionData.SentTo.ADDRESS_AND_MORE, tx.getSentTo());
    }

    @Test
    public void saysASendPaidTheWalletWhenEveryOutputDoes() {
        TransactionData tx = Wallet.transactionData(sent(), 10, 100, "");
        assertTrue(tx.getToYourself());
        assertEquals(TransactionData.SentTo.YOURSELF, tx.getSentTo());
    }

    @Test
    public void namesNoAddressWhenNoOtherOutputPaysOne() {
        TransactionData tx = Wallet.transactionData(sent(""), 10, 100, "");
        assertEquals("", tx.getToAddress());
        assertFalse(tx.getToYourself());
        assertEquals(TransactionData.SentTo.NO_ADDRESS, tx.getSentTo());
    }

    @Test
    public void namesNoRecipientOfAReceivedTransaction() {
        // The other output is the sender's change.
        Api.TransactionDetails received = Api.TransactionDetails.newBuilder()
                .addCredits(Api.TransactionDetails.Output.newBuilder().setAmount(5000))
                .addOtherOutputs(Api.TransactionDetails.OtherOutput.newBuilder().setAddress(PAYEE))
                .build();
        TransactionData tx = Wallet.transactionData(received, 10, 100, "");
        assertTrue(tx.getIncoming());
        assertEquals(5000, tx.getAmount());
        assertEquals("", tx.getToAddress());
        assertEquals(0, tx.getMoreRecipients());
        assertFalse(tx.getToYourself());
    }

    @Test
    public void givesTheReasonBchwalletRefusedACall() {
        String reason = "output of 100 satoshis to bitcoincash:qrhgt7m2w5362ltsnqdwxsnddd96kqde3q9c7qp34f: transaction output is dust";
        assertEquals(reason, Wallet.reasonFor(Status.INVALID_ARGUMENT.withDescription(reason).asRuntimeException()));
    }

    @Test
    public void hasNoReasonForOtherFailures() {
        assertNull(Wallet.reasonFor(Status.UNAVAILABLE.asRuntimeException()));
        assertNull(Wallet.reasonFor(new NumberFormatException("For input string: \"1..\"")));
    }

    @Test
    public void knowsAWrongPassphraseWhetherUnlockingOrChangingIt() {
        assertTrue(Wallet.isWrongPassphrase(Status.INVALID_ARGUMENT
                .withDescription("invalid passphrase for master private key").asRuntimeException()));
        assertTrue(Wallet.isWrongPassphrase(Status.INVALID_ARGUMENT
                .withDescription("invalid passphrase for private master key").asRuntimeException()));
    }

    @Test
    public void knowsOtherFailuresAreNotAWrongPassphrase() {
        assertFalse(Wallet.isWrongPassphrase(Status.INVALID_ARGUMENT
                .withDescription("Bytes do not represent a valid raw transaction").asRuntimeException()));
        assertFalse(Wallet.isWrongPassphrase(Status.UNKNOWN
                .withDescription("invalid passphrase for master private key").asRuntimeException()));
        assertFalse(Wallet.isWrongPassphrase(Status.INVALID_ARGUMENT.asRuntimeException()));
        assertFalse(Wallet.isWrongPassphrase(new IllegalStateException("invalid passphrase for master private key")));
    }
}
