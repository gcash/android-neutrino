package cash.bchd.android_neutrino;

import androidx.lifecycle.LifecycleOwner;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WalletCloserTest {

    /**
     * FakeWallets is the app's wallet, which runs or doesn't, and counts its starts and stops.
     */
    private static class FakeWallets implements WalletCloser.Wallets {
        boolean running = true;
        int starts;
        int stops;

        @Override
        public boolean exists() {
            return running;
        }

        @Override
        public void start() {
            starts++;
            running = true;
        }

        @Override
        public boolean stop() {
            if (!running) {
                return false;
            }
            stops++;
            running = false;
            return true;
        }
    }

    /**
     * FakeDelayer keeps the tasks posted, which the test runs when it lets their delay pass.
     */
    private static class FakeDelayer implements WalletCloser.Delayer {
        final List<Runnable> tasks = new ArrayList<>();
        long lastDelayMillis;

        @Override
        public void postDelayed(Runnable task, long delayMillis) {
            tasks.add(task);
            lastDelayMillis = delayMillis;
        }

        @Override
        public void cancel(Runnable task) {
            tasks.remove(task);
        }

        void letTimePass() {
            List<Runnable> due = new ArrayList<>(tasks);
            tasks.clear();
            for (Runnable task : due) {
                task.run();
            }
        }
    }

    private final FakeWallets wallets = new FakeWallets();
    private final FakeDelayer delayer = new FakeDelayer();
    private final WalletCloser closer = new WalletCloser(wallets, delayer, WalletCloser.CLOSE_DELAY_MILLIS);
    // The whole app, which goes to the background and comes back.
    private final LifecycleOwner app = () -> null;

    @Test
    public void stopsTheWalletTenMinutesAfterTheAppLeaves() {
        closer.onStop(app);
        assertEquals(1, delayer.tasks.size());
        assertEquals(TimeUnit.MINUTES.toMillis(10), delayer.lastDelayMillis);
        assertEquals(0, wallets.stops);

        delayer.letTimePass();
        assertEquals(1, wallets.stops);
    }

    @Test
    public void keepsTheWalletWhenTheAppComesBackInTime() {
        closer.onStop(app);
        closer.onStart(app);
        delayer.letTimePass();

        assertEquals(0, wallets.stops);
        assertEquals(0, wallets.starts);
    }

    @Test
    public void startsTheWalletAgainWhenTheAppComesBack() {
        closer.onStop(app);
        delayer.letTimePass();
        closer.onStart(app);
        assertEquals(1, wallets.starts);
        assertTrue(wallets.running);

        // It started the wallet it stopped, once.
        closer.onStop(app);
        closer.onStart(app);
        assertEquals(1, wallets.starts);
    }

    @Test
    public void leavesANewWalletToTheMainScreen() {
        // The app opens without a wallet, which the main screen creates, as only it keeps the new
        // wallet's recovery phrase.
        wallets.running = false;
        closer.onStart(app);
        assertEquals(0, wallets.starts);
    }

    @Test
    public void startsNoSecondWallet() {
        closer.onStop(app);
        delayer.letTimePass();
        // The main screen came back first, and started the wallet itself.
        wallets.running = true;
        closer.onStart(app);
        assertEquals(0, wallets.starts);
    }

    @Test
    public void keepsOneCountdown() {
        closer.onStop(app);
        closer.onStop(app);
        assertEquals(1, delayer.tasks.size());
    }
}
