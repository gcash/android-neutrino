package cash.bchd.android_neutrino.wallet;

import org.junit.Test;

import io.grpc.Status;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WalletTest {

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
