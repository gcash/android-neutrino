package cash.bchd.android_neutrino.wallet;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import walletrpc.Api;
import walletrpc.WalletServiceGrpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NotificationStreamsTest {

    // The pauses before subscribing again, short so that the tests are quick.
    private static final long FIRST_DELAY_MILLIS = 10;
    private static final long MAX_DELAY_MILLIS = 40;

    /**
     * FakeWallet keeps the streams that clients subscribe to, for a test to end.
     */
    private static class FakeWallet extends WalletServiceGrpc.WalletServiceImplBase {
        final BlockingQueue<StreamObserver<Api.TransactionNotificationsResponse>> transactions =
                new LinkedBlockingQueue<>();
        final BlockingQueue<StreamObserver<Api.RescanNotificationsResponse>> rescans = new LinkedBlockingQueue<>();

        @Override
        public void transactionNotifications(Api.TransactionNotificationsRequest request,
                                             StreamObserver<Api.TransactionNotificationsResponse> observer) {
            transactions.add(observer);
        }

        @Override
        public void rescanNotifications(Api.RescanNotificationsRequest request,
                                        StreamObserver<Api.RescanNotificationsResponse> observer) {
            rescans.add(observer);
        }
    }

    /**
     * RecordingHandler records what the streams hand it, and fails to refresh when told to.
     */
    private static class RecordingHandler implements NotificationStreams.Handler {
        final BlockingQueue<Api.TransactionNotificationsResponse> transactions = new LinkedBlockingQueue<>();
        final BlockingQueue<Api.RescanNotificationsResponse> rescans = new LinkedBlockingQueue<>();
        final BlockingQueue<Boolean> refreshes = new LinkedBlockingQueue<>();
        volatile boolean failRefresh;

        @Override
        public void onTransactions(Api.TransactionNotificationsResponse value) {
            transactions.add(value);
        }

        @Override
        public void onRescan(Api.RescanNotificationsResponse value) {
            rescans.add(value);
        }

        @Override
        public void refresh() throws Exception {
            refreshes.add(true);
            if (failRefresh) {
                throw new Exception("the wallet can't be reached");
            }
        }
    }

    private Server server;
    private ManagedChannel channel;
    private ScheduledExecutorService scheduler;
    private FakeWallet wallet;
    private RecordingHandler handler;
    private NotificationStreams streams;
    private final List<String> warnings = new CopyOnWriteArrayList<>();

    @Before
    public void setUp() throws Exception {
        String name = InProcessServerBuilder.generateName();
        wallet = new FakeWallet();
        server = InProcessServerBuilder.forName(name).directExecutor().addService(wallet).build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        handler = new RecordingHandler();
        streams = new NotificationStreams(WalletServiceGrpc.newStub(channel), scheduler, handler,
                FIRST_DELAY_MILLIS, MAX_DELAY_MILLIS, warnings::add);
    }

    @After
    public void tearDown() {
        streams.stop();
        channel.shutdownNow();
        server.shutdownNow();
        scheduler.shutdownNow();
    }

    private static <T> T take(BlockingQueue<T> queue) throws InterruptedException {
        T value = queue.poll(5, TimeUnit.SECONDS);
        assertNotNull("timed out", value);
        return value;
    }

    // settle waits until the scheduler has run what it was given so far, such as subscribing
    // again and refreshing.
    private void settle() throws Exception {
        scheduler.submit(() -> {
        }).get(5, TimeUnit.SECONDS);
    }

    // pauses returns the pauses that the warnings announced, in order.
    private List<String> pauses() {
        List<String> out = new ArrayList<>();
        for (String w : warnings) {
            int i = w.indexOf("subscribing again in ");
            if (i >= 0) {
                out.add(w.substring(i + "subscribing again in ".length()));
            }
        }
        return out;
    }

    @Test
    public void subscribesAgainWhenTheConnectionCloses() throws Exception {
        streams.start();
        StreamObserver<Api.TransactionNotificationsResponse> first = take(wallet.transactions);
        take(wallet.rescans);
        first.onNext(Api.TransactionNotificationsResponse.getDefaultInstance());
        take(handler.transactions);

        // grpc-java's OkHttp transport closed the connection every 10,000 calls, which ended
        // the stream, and the balance on screen stopped changing.
        first.onError(Status.UNAVAILABLE.withDescription("connection closed").asRuntimeException());
        StreamObserver<Api.TransactionNotificationsResponse> second = take(wallet.transactions);
        take(handler.refreshes);
        second.onNext(Api.TransactionNotificationsResponse.getDefaultInstance());
        take(handler.transactions);

        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0), warnings.get(0).startsWith("Transaction notifications ended (Status{code=UNAVAILABLE"));
        assertTrue(warnings.get(0), warnings.get(0).endsWith("subscribing again in 10 ms"));
        // The rescan stream was left alone.
        assertNull(wallet.rescans.poll());
    }

    @Test
    public void subscribesAgainWhenTheWalletEndsAStream() throws Exception {
        streams.start();
        take(wallet.transactions);
        StreamObserver<Api.RescanNotificationsResponse> first = take(wallet.rescans);

        first.onCompleted();
        StreamObserver<Api.RescanNotificationsResponse> second = take(wallet.rescans);
        take(handler.refreshes);
        second.onNext(Api.RescanNotificationsResponse.newBuilder().setHeight(42).build());
        assertEquals(42, take(handler.rescans).getHeight());
        assertTrue(warnings.get(0), warnings.get(0).startsWith("Rescan notifications ended (Status{code=OK"));
    }

    @Test
    public void pausesLongerWhileRefreshingFails() throws Exception {
        streams.start();
        take(wallet.rescans);
        StreamObserver<Api.TransactionNotificationsResponse> stream = take(wallet.transactions);

        // The wallet can't be reached twice, and then can.
        handler.failRefresh = true;
        for (int i = 0; i < 3; i++) {
            if (i == 2) {
                handler.failRefresh = false;
            }
            stream.onError(Status.UNAVAILABLE.asRuntimeException());
            stream = take(wallet.transactions);
            take(handler.refreshes);
            settle();
        }
        stream.onError(Status.UNAVAILABLE.asRuntimeException());
        take(wallet.transactions);
        settle();

        List<String> expected = new ArrayList<>();
        expected.add("10 ms");
        expected.add("20 ms");
        expected.add("40 ms");
        expected.add("10 ms");
        assertEquals(expected, pauses());
    }

    @Test
    public void eachStreamPausesOnItsOwn() throws Exception {
        streams.start();
        StreamObserver<Api.TransactionNotificationsResponse> transactions = take(wallet.transactions);
        StreamObserver<Api.RescanNotificationsResponse> rescans = take(wallet.rescans);

        // A closed connection ends both streams at once, and neither pauses longer for it.
        transactions.onError(Status.UNAVAILABLE.asRuntimeException());
        rescans.onError(Status.UNAVAILABLE.asRuntimeException());
        take(wallet.transactions);
        take(wallet.rescans);
        settle();

        List<String> expected = new ArrayList<>();
        expected.add("10 ms");
        expected.add("10 ms");
        assertEquals(expected, pauses());
    }

    @Test
    public void refreshesOnceWhenBothStreamsEnd() throws Exception {
        streams.start();
        StreamObserver<Api.TransactionNotificationsResponse> transactions = take(wallet.transactions);
        StreamObserver<Api.RescanNotificationsResponse> rescans = take(wallet.rescans);

        // A closed connection ends both, and each refreshing read the whole history.
        transactions.onError(Status.UNAVAILABLE.asRuntimeException());
        rescans.onError(Status.UNAVAILABLE.asRuntimeException());
        take(wallet.transactions);
        take(wallet.rescans);
        take(handler.refreshes);
        Thread.sleep(5 * MAX_DELAY_MILLIS);
        settle();
        assertNull(handler.refreshes.poll());
    }

    @Test
    public void staysStoppedOnceStopped() throws Exception {
        streams.start();
        StreamObserver<Api.TransactionNotificationsResponse> transactions = take(wallet.transactions);
        StreamObserver<Api.RescanNotificationsResponse> rescans = take(wallet.rescans);

        streams.stop();
        assertTrue(((ServerCallStreamObserver<?>) transactions).isCancelled());
        assertTrue(((ServerCallStreamObserver<?>) rescans).isCancelled());
        Thread.sleep(5 * MAX_DELAY_MILLIS);
        settle();
        assertNull(wallet.transactions.poll());
        assertNull(wallet.rescans.poll());
        assertNull(handler.refreshes.poll());
        assertTrue(warnings.toString(), warnings.isEmpty());
    }
}
