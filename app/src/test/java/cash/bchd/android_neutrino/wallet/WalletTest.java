package cash.bchd.android_neutrino.wallet;

import org.junit.Test;

import io.grpc.Status;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
}
