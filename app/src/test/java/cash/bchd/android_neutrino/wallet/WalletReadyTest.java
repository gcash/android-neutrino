package cash.bchd.android_neutrino.wallet;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WalletReadyTest {

    @Test
    public void givesWhatsReadyAtOnce() throws Exception {
        int listening = Wallet.readyListenerCount();
        ListenableFuture<String> ready = Wallet.firstReady(() -> "wallet");
        assertTrue(ready.isDone());
        assertEquals("wallet", ready.get());
        assertEquals(listening, Wallet.readyListenerCount());
    }

    @Test
    public void waitsForAWalletToLoad() throws Exception {
        int listening = Wallet.readyListenerCount();
        AtomicReference<String> current = new AtomicReference<>();
        ListenableFuture<String> ready = Wallet.firstReady(current::get);
        assertFalse(ready.isDone());

        // A wallet that loaded isn't this one's yet, as when another is still stopping.
        Wallet.tellReady();
        assertFalse(ready.isDone());

        current.set("wallet");
        Wallet.tellReady();
        assertTrue(ready.isDone());
        assertEquals("wallet", ready.get());
        assertEquals(listening, Wallet.readyListenerCount());
    }

    @Test
    public void stopsListeningWhenCancelled() {
        int listening = Wallet.readyListenerCount();
        ListenableFuture<String> ready = Wallet.firstReady(() -> null);
        assertEquals(listening + 1, Wallet.readyListenerCount());
        ready.cancel(true);
        assertEquals(listening, Wallet.readyListenerCount());
    }
}
