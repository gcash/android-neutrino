package cash.bchd.android_neutrino.wallet;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.grpc.Status;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import walletrpc.Api;
import walletrpc.WalletServiceGrpc;

/**
 * NotificationStreams keeps the wallet's transaction and rescan notification streams open. A
 * stream ends with its connection, which grpc-java's OkHttp transport closed every 10,000 calls
 * while the wallet answered each request with a ping, and the balance on screen then stopped
 * changing. When a stream ends while the wallet runs, it subscribes again after a pause and has
 * the handler refresh, since what happened in between wasn't notified. Each stream's pause
 * doubles, up to the longest, while refreshing after it fails, and goes back to the first once
 * refreshing succeeds.
 */
final class NotificationStreams {

    /**
     * Handler gets the streams' notifications, and refreshes after a stream started again.
     */
    interface Handler {
        void onTransactions(Api.TransactionNotificationsResponse value);

        void onRescan(Api.RescanNotificationsResponse value);

        /**
         * refresh reads again what the streams would have told, and throws when it can't.
         */
        void refresh() throws Exception;
    }

    /**
     * Stream is one of the two streams: how to subscribe to it, its call, and its pause.
     */
    private final class Stream {
        final String name;
        final Runnable subscribe;
        ClientCallStreamObserver<?> call;
        long delayMillis = firstDelayMillis;

        Stream(String name, Runnable subscribe) {
            this.name = name;
            this.subscribe = subscribe;
        }
    }

    private final WalletServiceGrpc.WalletServiceStub stub;
    private final ScheduledExecutorService scheduler;
    private final Handler handler;
    private final long firstDelayMillis;
    private final long maxDelayMillis;
    private final Consumer<String> warn;

    private final Object lock = new Object();
    private boolean stopped;
    // How many streams ended and wait to be subscribed again. Both end together when their
    // connection closes, and one refresh, once the last is back, covers what both missed.
    private int waiting;
    private final Stream transactions;
    private final Stream rescans;

    NotificationStreams(WalletServiceGrpc.WalletServiceStub stub, ScheduledExecutorService scheduler,
                        Handler handler, long firstDelayMillis, long maxDelayMillis, Consumer<String> warn) {
        this.stub = stub;
        this.scheduler = scheduler;
        this.handler = handler;
        this.firstDelayMillis = firstDelayMillis;
        this.maxDelayMillis = maxDelayMillis;
        this.warn = warn;
        this.transactions = new Stream("Transaction notifications", this::subscribeTransactions);
        this.rescans = new Stream("Rescan notifications", this::subscribeRescans);
    }

    void start() {
        subscribeTransactions();
        subscribeRescans();
    }

    /**
     * stop cancels the streams, and keeps them from being subscribed again.
     */
    void stop() {
        ClientCallStreamObserver<?> t;
        ClientCallStreamObserver<?> r;
        synchronized (lock) {
            stopped = true;
            t = transactions.call;
            r = rescans.call;
        }
        if (t != null) {
            t.cancel("the wallet stopped", null);
        }
        if (r != null) {
            r.cancel("the wallet stopped", null);
        }
    }

    private void subscribeTransactions() {
        stub.transactionNotifications(Api.TransactionNotificationsRequest.getDefaultInstance(),
                new ClientResponseObserver<Api.TransactionNotificationsRequest, Api.TransactionNotificationsResponse>() {
                    @Override
                    public void beforeStart(ClientCallStreamObserver<Api.TransactionNotificationsRequest> call) {
                        started(transactions, call);
                    }

                    @Override
                    public void onNext(Api.TransactionNotificationsResponse value) {
                        handler.onTransactions(value);
                    }

                    @Override
                    public void onError(Throwable t) {
                        ended(transactions, Status.fromThrowable(t));
                    }

                    @Override
                    public void onCompleted() {
                        ended(transactions, Status.OK);
                    }
                });
    }

    private void subscribeRescans() {
        stub.rescanNotifications(Api.RescanNotificationsRequest.getDefaultInstance(),
                new ClientResponseObserver<Api.RescanNotificationsRequest, Api.RescanNotificationsResponse>() {
                    @Override
                    public void beforeStart(ClientCallStreamObserver<Api.RescanNotificationsRequest> call) {
                        started(rescans, call);
                    }

                    @Override
                    public void onNext(Api.RescanNotificationsResponse value) {
                        handler.onRescan(value);
                    }

                    @Override
                    public void onError(Throwable t) {
                        ended(rescans, Status.fromThrowable(t));
                    }

                    @Override
                    public void onCompleted() {
                        ended(rescans, Status.OK);
                    }
                });
    }

    /**
     * started keeps the stream's call, so that stop can cancel it, or cancels it if the streams
     * were stopped.
     */
    private void started(Stream stream, ClientCallStreamObserver<?> call) {
        boolean wasStopped;
        synchronized (lock) {
            stream.call = call;
            wasStopped = stopped;
        }
        if (wasStopped) {
            call.cancel("the wallet stopped", null);
        }
    }

    private void ended(Stream stream, Status status) {
        long delay;
        synchronized (lock) {
            if (stopped) {
                return;
            }
            delay = stream.delayMillis;
            stream.delayMillis = Math.min(stream.delayMillis * 2, maxDelayMillis);
            waiting++;
        }
        warn.accept(stream.name + " ended (" + status + "), subscribing again in " + delay + " ms");
        scheduler.schedule(() -> {
            synchronized (lock) {
                if (stopped) {
                    return;
                }
            }
            stream.subscribe.run();
            synchronized (lock) {
                if (--waiting > 0) {
                    return;
                }
            }
            try {
                handler.refresh();
                synchronized (lock) {
                    transactions.delayMillis = firstDelayMillis;
                    rescans.delayMillis = firstDelayMillis;
                }
            } catch (Exception e) {
                warn.accept(stream.name + " started again, but refreshing failed: " + e);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }
}
