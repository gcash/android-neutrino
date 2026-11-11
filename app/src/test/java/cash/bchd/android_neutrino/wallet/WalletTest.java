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
    public void namesATransactionByItsDoubleHash() {
        // Bitcoin's genesis coinbase, whose ID is well known.
        byte[] genesis = com.google.common.io.BaseEncoding.base16().lowerCase().decode(
                "01000000010000000000000000000000000000000000000000000000000000000000000000ffffffff"
                        + "4d04ffff001d0104455468652054696d65732030332f4a616e2f32303039204368616e63656c"
                        + "6c6f72206f6e206272696e6b206f66207365636f6e64206261696c6f757420666f722062616e"
                        + "6b73ffffffff0100f2052a01000000434104678afdb0fe5548271967f1a67130b7105cd6a828"
                        + "e03909a67962e0ea1f61deb649f6bc3f4cef38c4f35504e51ec112de5c384df7ba0b8d578a4c"
                        + "702b6bf11d5fac00000000");
        assertEquals("4a5e1e4baab89f3a32518a88c31bc87f618f76673e2cc77ab2127b7afdeda33b",
                Wallet.txidOf(genesis));
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

    @Test
    public void tellsReadyListenersUntilTheyLeave() {
        int[] told = {0};
        Runnable listener = () -> told[0]++;
        Wallet.addReadyListener(listener);
        Wallet.tellReady();
        Wallet.removeReadyListener(listener);
        Wallet.tellReady();
        org.junit.Assert.assertEquals(1, told[0]);
    }

    @Test
    public void tellsGatewaysBusyFromOtherFailures() {
        org.junit.Assert.assertTrue(Wallet.gatewaysBusy(io.grpc.Status.RESOURCE_EXHAUSTED.asRuntimeException()));
        org.junit.Assert.assertFalse(Wallet.gatewaysBusy(io.grpc.Status.UNAVAILABLE.asRuntimeException()));
        org.junit.Assert.assertFalse(Wallet.gatewaysBusy(new RuntimeException("not gRPC")));
    }

    // fusionOf returns a transaction of the inputs and outputs of others besides two inputs and
    // three outputs of the wallet's, with the OP_RETURN output, of the value, that opens with the
    // bytes, which a fusion's does with OP_RETURN, a push of 4, "FUZ\0" and the session hash.
    private static Api.TransactionDetails fusionOf(int yourInputs, byte[] opReturn, long value) {
        Api.TransactionDetails.Builder tx = Api.TransactionDetails.newBuilder().setInputCount(42);
        for (int i = 0; i < yourInputs; i++) {
            tx.addDebits(Api.TransactionDetails.Input.newBuilder().setPreviousAmount(100000));
        }
        for (int i = 0; i < 3; i++) {
            tx.addCredits(Api.TransactionDetails.Output.newBuilder().setAmount(66000));
        }
        tx.addOtherOutputs(Api.TransactionDetails.OtherOutput.newBuilder().setAmount(value)
                .setOpReturn(com.google.protobuf.ByteString.copyFrom(opReturn)));
        for (int i = 0; i < 34; i++) {
            tx.addOtherOutputs(Api.TransactionDetails.OtherOutput.newBuilder().setAddress(PAYEE).setAmount(70000));
        }
        return tx.build();
    }

    private static byte[] script(int... bytes) {
        byte[] script = new byte[bytes.length + 32];
        for (int i = 0; i < bytes.length; i++) {
            script[i] = (byte) bytes[i];
        }
        return script;
    }

    @Test
    public void tellsACashFusion() {
        byte[] fuz = script(0x6a, 0x04, 'F', 'U', 'Z', 0x00, 0x20);
        TransactionData.Fusion fusion = Wallet.fusion(fusionOf(2, fuz, 0));
        assertEquals(new TransactionData.Fusion(42, 38, 2, 3), fusion);
        assertEquals(fusion, Wallet.transactionData(fusionOf(2, fuz, 0), 0, 0, "").getFusion());

        // None of the wallet's inputs: anyone can write the marker.
        org.junit.Assert.assertNull(Wallet.fusion(fusionOf(0, fuz, 0)));
        // An OP_RETURN of another protocol, one cut short, and one of value.
        org.junit.Assert.assertNull(Wallet.fusion(fusionOf(2, script(0x6a, 0x04, 'S', 'L', 'P', 0x00), 0)));
        org.junit.Assert.assertNull(Wallet.fusion(fusionOf(2, new byte[]{0x6a, 0x04, 'F', 'U', 'Z'}, 0)));
        org.junit.Assert.assertNull(Wallet.fusion(fusionOf(2, fuz, 546)));
        // A payment.
        org.junit.Assert.assertNull(Wallet.transactionData(sent(PAYEE), 0, 0, "").getFusion());
    }
}
