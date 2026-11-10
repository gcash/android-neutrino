package cash.bchd.android_neutrino.wallet;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.google.protobuf.ByteString;

import java.io.File;
import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import walletrpc.Api;
import walletrpc.WalletLoaderServiceGrpc;
import walletrpc.WalletServiceGrpc;

/**
 * Wallet represents an instance of a bchwallet. It will load and start the
 * bchwallet daemon and provide convience methods to the wallet's API calls.
 */
public class Wallet implements Serializable {

    private static Wallet instance;

    private Config config;

    private String getConfigFilePath;
    private final String host = "127.0.0.1";
    // What the wallet can answer with: all the NFT types of the largest registry it reads, 16 MiB,
    // and room for how the reply writes them.
    private static final int MAX_REPLY_SIZE = 32 << 20;
    // How long a call the user waits on has to answer, as a wallet that's busy or gone shouldn't
    // leave a screen waiting for good.
    private static final long REPLY_DEADLINE_SECONDS = 10;

    private ManagedChannel channel;
    public static final io.grpc.Context.Key<String> AUTH_TOKEN_KEY = io.grpc.Context.key("AuthenticationToken");
    private AuthCredentials creds;

    public final static String DEFAULT_PASSPHRASE = "LETMEIN";

    private HashMap<String, AddressListener> addressListeners = new HashMap<String, AddressListener>();
    // The memos of the transactions the app sent, which the wallet doesn't have, by transaction ID.
    private HashMap<String, String> memos = new HashMap<String, String>();
    // The listeners told of each new block, from any thread.
    private final List<WalletEventListener> blockchainListeners = new CopyOnWriteArrayList<>();
    // The listener of the screen that shows the wallet, which the notification streams tell of
    // the wallet's balance and transactions, and the streams, which its first listener starts.
    private volatile WalletEventListener mainListener;
    private NotificationStreams streams;

    private int lastBlockHeight;
    private String lastBlockHash;

    // The wallet's transactions as the app last read them all, kept up to date from the
    // notifications, and what's under way with them, which historyLock guards: whether a read of
    // them all is, and the listeners of one to make once it's done, as a read that began before a
    // notification can miss what it told; and whether they're to be told, to which listener, and
    // when they were told last, as they're told at most every HISTORY_GAP_NANOS.
    private final Object historyLock = new Object();
    private final TransactionHistory history = new TransactionHistory();
    private boolean historyRead;
    private final ReadQueue<WalletEventListener> reads = new ReadQueue<>();
    private boolean historyToTell;
    private WalletEventListener historyFor;
    private long historyToldAt = System.nanoTime() - HISTORY_GAP_NANOS;
    // How often at most the transactions are told while notifications keep coming, as during a
    // sync, where each telling has the screens read and save them all.
    private static final long HISTORY_GAP_NANOS = TimeUnit.SECONDS.toNanos(3);
    // Tells the transactions, off the notifications' thread.
    private static final ScheduledExecutorService HISTORY = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "wallet-history");
        thread.setDaemon(true);
        return thread;
    });

    private static boolean running = false;
    // Those to tell when a wallet has loaded.
    private static final Set<Runnable> READY_LISTENERS = new CopyOnWriteArraySet<>();

    // Set once stop was called, so that loadWallet does not report the
    // shutdown as an error.
    private volatile boolean stopped = false;

    // Done once start has started the daemon.
    private volatile Future<?> started;

    // How long loadWallet waits between checks whether the daemon is ready.
    private static final long POLL_MILLIS = 250;

    // Starts and stops the wallet daemon one after the other, off the UI
    // thread, since stopping it waits for it to close its databases.
    private static final ExecutorService lifecycle = Executors.newSingleThreadExecutor();

    // Subscribes again to the notification streams that ended, after a pause from the first
    // to the longest.
    private static final ScheduledExecutorService notificationsScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "wallet-notifications");
                thread.setDaemon(true);
                return thread;
            });
    // Asks for the wallet's current address, which can take a while when the wallet is busy, as
    // during a sync, so never on the UI thread.
    private static final ListeningExecutorService ADDRESSES = MoreExecutors.listeningDecorator(
            Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "wallet-addresses");
                thread.setDaemon(true);
                return thread;
            }));
    private static final long STREAM_FIRST_DELAY_MILLIS = 1000;
    private static final long STREAM_MAX_DELAY_MILLIS = 30000;

    private static final String TAG = "Wallet";

    /**
     * The wallet constructor takes in a context which it uses to derive the config file
     * path and appdatadir.
     */
    public Wallet(Context context, Config config) {
        this.config = config;
        this.getConfigFilePath = config.getConfigFilePath();
        this.creds = new AuthCredentials(config.getAuthToken());
        this.channel = ManagedChannelBuilder.forAddress(this.host, config.getRpcPort()).usePlaintext()
                .maxInboundMessageSize(MAX_REPLY_SIZE).build();
        instance = this;
        try {
            config.save(context);
        } catch (Exception e) {
            e.printStackTrace();
        }
        BitcoinPaymentURI.SCHEME = config.getNetwork().uriPrefix();

        File directory = new File(config.getDataDir()+File.separator+config.getNetwork().id);
        directory.mkdirs();
    }

    /**
     * addReadyListener has the listener told, on no particular thread, each time a wallet has
     * loaded, as one does some time after the app comes back to a screen that needs it. That's
     * once the wallet tells its main listener, so the screen can take that over.
     */
    public static void addReadyListener(Runnable listener) {
        READY_LISTENERS.add(listener);
    }

    /**
     * removeReadyListener stops telling the listener.
     */
    public static void removeReadyListener(Runnable listener) {
        READY_LISTENERS.remove(listener);
    }

    static void tellReady() {
        for (Runnable listener : READY_LISTENERS) {
            listener.run();
        }
    }

    /**
     * whenRunning returns the wallet once one is running: at once if one is, or once the next one
     * has loaded, as when the app has just started or the wallet is starting again. Cancelling it
     * stops waiting.
     */
    public static ListenableFuture<Wallet> whenRunning() {
        return firstReady(() -> {
            Wallet wallet = instance;
            return wallet != null && wallet.isRunning() ? wallet : null;
        });
    }

    /**
     * firstReady returns what current gives once it gives something: at once if it does now, or
     * when a wallet has loaded and it does then. Once it's done, or cancelled, it stops listening.
     */
    static <T> ListenableFuture<T> firstReady(Supplier<T> current) {
        SettableFuture<T> ready = SettableFuture.create();
        Runnable listener = () -> {
            T value = current.get();
            if (value != null) {
                ready.set(value);
            }
        };
        // Listening first, so that a wallet that loads after the check below isn't missed.
        addReadyListener(listener);
        ready.addListener(() -> removeReadyListener(listener), MoreExecutors.directExecutor());
        listener.run();
        return ready;
    }

    // readyListenerCount returns how many are waiting for a wallet to load, for tests.
    static int readyListenerCount() {
        return READY_LISTENERS.size();
    }

    /**
     * currentAddressesAsync returns both forms of the current address of the wallet that runs,
     * once one does, asked for off the UI thread. It has no deadline, as a wallet that's busy
     * answers late, and cancelling it stops waiting.
     */
    public static ListenableFuture<Addresses> currentAddressesAsync() {
        return Futures.transformAsync(whenRunning(), wallet -> ADDRESSES.submit(wallet::currentAddresses),
                MoreExecutors.directExecutor());
    }

    public static Wallet getInstance() {
        return instance;
    }

    /**
     * runAfterStop runs the task in the background once the wallets stopped so far have shut
     * down, and before wallets started later start up. Use it to change the wallet's files.
     */
    public static void runAfterStop(Runnable task) {
        lifecycle.execute(task);
    }

    public static void resetInstance() {
        instance = null;
    }

    /**
     * loadWallet waits in the background for the daemon to start, creates the wallet from the
     * mnemonic, or a new one if it is empty, when it does not exist yet, and starts listening for
     * the wallet's events once it is loaded. If the daemon stops or the wallet can't be created,
     * for example because the mnemonic is invalid, the listener's onWalletError is called.
     */
    public void loadWallet(WalletEventListener listener, String mnemonic) {
        Thread thread = new Thread(() -> {
            try {
                Future<?> start = started;
                if (start != null) {
                    start.get();
                }

                // The loader service answers once the daemon's RPC server is up.
                boolean exists = waitForDaemon(this::walletExists);
                if (!exists) {
                    String mnemonicToSet = mnemonic;
                    if (mnemonicToSet.equals("")) {
                        mnemonicToSet = generateMnemonic();
                    }
                    WalletLoaderServiceGrpc.WalletLoaderServiceBlockingStub stub = WalletLoaderServiceGrpc.newBlockingStub(this.channel).withCallCredentials(this.creds);
                    ByteString pw = ByteString.copyFromUtf8(DEFAULT_PASSPHRASE);

                    Api.CreateWalletRequest request = Api.CreateWalletRequest.newBuilder()
                            .setPrivatePassphrase(pw)
                            .setMnemonicSeed(mnemonicToSet)
                            .setWalletBirthday(this.config.getBirthday())
                            .build();
                    stub.createWallet(request);
                    listener.onWalletCreated(mnemonicToSet);
                }

                // The wallet service answers once the wallet is loaded.
                waitForDaemon(() -> {
                    WalletServiceGrpc.WalletServiceBlockingStub stub = WalletServiceGrpc.newBlockingStub(channel).withCallCredentials(creds);
                    return stub.ping(Api.PingRequest.newBuilder().build());
                });
                running = true;
                listener.onWalletReady();
                tellBalances(listener);
                getTransactions(listener);
                listenTransactions(listener);
                tellReady();
            } catch (Exception e) {
                if (!stopped) {
                    listener.onWalletError(describe(e));
                }
            }
        }, "wallet-load");
        thread.start();
    }

    private interface DaemonCall<T> {
        T call() throws Exception;
    }

    /**
     * waitForDaemon makes the call until the daemon answers it, while the daemon is still starting
     * up. It throws when the daemon stopped, or the call failed for another reason.
     */
    private <T> T waitForDaemon(DaemonCall<T> call) throws Exception {
        while (true) {
            try {
                return call.call();
            } catch (StatusRuntimeException e) {
                // The call is unavailable until the RPC server listens, and fails
                // its precondition until the service it uses is ready.
                Status.Code code = e.getStatus().getCode();
                if (code != Status.Code.UNAVAILABLE && code != Status.Code.FAILED_PRECONDITION) {
                    throw e;
                }
            }
            if (stopped || !mobile.Mobile.isRunning()) {
                String err = mobile.Mobile.lastError();
                throw new Exception(err.isEmpty() ? "The wallet stopped" : err);
            }
            Thread.sleep(POLL_MILLIS);
            // Connect right away instead of after the channel's backoff once
            // the RPC server listens.
            channel.resetConnectBackoff();
        }
    }

    private static String describe(Exception e) {
        String reason = reasonFor(e);
        if (reason != null) {
            return reason;
        }
        return e.getMessage() != null ? e.getMessage() : e.toString();
    }

    /**
     * reasonFor returns the reason bchwallet gave for failing a call that threw t, such as why
     * it refused to create a transaction, or null if the failure didn't come with one.
     */
    public static String reasonFor(Throwable t) {
        if (t instanceof StatusRuntimeException) {
            return ((StatusRuntimeException) t).getStatus().getDescription();
        }
        return null;
    }

    /**
     * isWrongPassphrase returns whether bchwallet failed a call that threw t because the private
     * passphrase doesn't unlock the wallet. Unlocking and changing the passphrase word the reason
     * differently, "invalid passphrase for master private key" and "for private master key".
     */
    public static boolean isWrongPassphrase(Throwable t) {
        if (!(t instanceof StatusRuntimeException)) {
            return false;
        }
        Status status = ((StatusRuntimeException) t).getStatus();
        String description = status.getDescription();
        return status.getCode() == Status.Code.INVALID_ARGUMENT && description != null
                && description.startsWith("invalid passphrase for ");
    }

    public boolean isRunning() {
        return running;
    }

    public String uriPrefix() {
        return config.getNetwork().uriPrefix();
    }

    public Network getNetwork() {
        return config.getNetwork();
    }

    /**
     * listenTransactions tells the listener, the one of the screen that shows the wallet, of the
     * wallet's new blocks, balance and transactions, in place of the listener before it. The
     * notification streams it starts the first time run until the wallet stops, and subscribe
     * again when they end, as when their connection closes.
     */
    public void listenTransactions(WalletEventListener listener) {
        setMainListener(listener);
        try {
            Api.NetworkResponse network = network();
            lastBlockHeight = network.getBestHeight();
            lastBlockHash = network.getBestBlock();

            listener.onBlock(lastBlockHeight, lastBlockHash);
        } catch (Exception e) {
            e.printStackTrace();
        }

        synchronized (this) {
            if (streams != null || stopped) {
                return;
            }
            WalletServiceGrpc.WalletServiceStub stub = WalletServiceGrpc.newStub(channel).withCallCredentials(creds);
            streams = new NotificationStreams(stub, notificationsScheduler, new NotificationStreams.Handler() {
                @Override
                public void onTransactions(Api.TransactionNotificationsResponse value) {
                    onTransactionNotification(value);
                }

                @Override
                public void onRescan(Api.RescanNotificationsResponse value) {
                    WalletEventListener main = mainListener;
                    if (main != null) {
                        onRescanNotification(value, main);
                    }
                }

                @Override
                public void refresh() throws Exception {
                    WalletEventListener main = mainListener;
                    if (main != null) {
                        Wallet.this.refresh(main);
                    }
                }
            }, STREAM_FIRST_DELAY_MILLIS, STREAM_MAX_DELAY_MILLIS, message -> Log.w(TAG, message));
            streams.start();
        }
    }

    /**
     * setMainListener has the notification streams tell the listener, the one of the screen that
     * shows the wallet, in place of the listener before it, and returns whether it replaced one.
     * A screen that comes back in front of another one that had taken them takes them back.
     */
    public synchronized boolean setMainListener(WalletEventListener listener) {
        WalletEventListener previous = mainListener;
        if (previous == listener) {
            return false;
        }
        mainListener = listener;
        if (previous != null) {
            blockchainListeners.remove(previous);
        }
        blockchainListeners.add(listener);
        return true;
    }

    private void onTransactionNotification(Api.TransactionNotificationsResponse value) {
        List<Api.BlockDetails> blocks = value.getAttachedBlocksList();
        if (value.getDetachedBlocksCount() > 0) {
            lastBlockHeight = 0;
        }
        boolean hasMinedTransactions = false;
        for (Api.BlockDetails block : blocks) {
            if (block.getHeight() > lastBlockHeight) {
                lastBlockHeight = block.getHeight();
                lastBlockHash = txidToString(block.getHash());

                for (WalletEventListener blockchainListener : blockchainListeners) {
                    blockchainListener.onBlock(lastBlockHeight, lastBlockHash);
                }
            }
            if (block.getTransactionsCount() > 0) {
                hasMinedTransactions = true;
                for (Api.TransactionDetails tx : block.getTransactionsList()) {
                    for (Api.TransactionDetails.Output output : tx.getCreditsList()) {
                        AddressListener addrListener = (AddressListener) addressListeners.get(output.getAddress());
                        if (addrListener != null) {
                            addrListener.onPaymentReceived(output.getAmount());
                        }
                    }
                }
            }
        }

        boolean hasUnMinedTransactions = false;
        if (value.getUnminedTransactionsCount() > 0 || hasMinedTransactions) {
            hasUnMinedTransactions = true;
            for (Api.TransactionDetails tx : value.getUnminedTransactionsList()) {
                for (Api.TransactionDetails.Output output : tx.getCreditsList()) {
                    AddressListener addrListener = (AddressListener) addressListeners.get(output.getAddress());
                    if (addrListener != null) {
                        addrListener.onPaymentReceived(output.getAmount());
                    }
                }
            }
        }

        WalletEventListener listener = mainListener;
        if (listener == null) {
            return;
        }
        if (hasMinedTransactions || hasUnMinedTransactions) {
            try {
                tellBalances(listener);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // The listener rebuilds its list from all of the wallet's transactions, which the
        // notification brings up to date. A reorg has them all read again, as it moved the
        // transactions of the blocks it detached, and so does a notification that comes before
        // they were read, or as they're read.
        boolean readAgain = value.getDetachedBlocksCount() > 0;
        boolean changed = false;
        if (!readAgain) {
            synchronized (historyLock) {
                if (historyRead && !reads.reading()) {
                    changed = history.apply(value, this::extractTransactionData);
                } else {
                    readAgain = true;
                }
            }
        }
        try {
            if (readAgain) {
                getTransactions(listener);
            } else if (changed) {
                tellHistory(listener);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * refresh tells the listener the wallet's tip, balance, tokens and transactions again, as when
     * it may have missed notifications of them. It throws when the wallet doesn't answer.
     */
    public void refresh(WalletEventListener listener) throws Exception {
        // A refresh is for notifications that may have been missed, so until its read is done,
        // or if it fails before, the next notification reads them all too.
        synchronized (historyLock) {
            historyRead = false;
        }
        Api.NetworkResponse net = network();
        listener.onBlock(net.getBestHeight(), net.getBestBlock());
        tellBalances(listener);
        getTransactions(listener);
    }

    // tellBalances tells the listener the wallet's spendable balance, then its tokens, which the
    // same transactions change, unless a later ask's were told first.
    private void tellBalances(WalletEventListener listener) throws Exception {
        long replyNumber = listener.balanceReplies.next();
        long balance = balance();
        List<TokenBalance> tokens = tokenBalances();
        listener.balanceReplies.deliver(replyNumber, () -> {
            // A wallet that was stopped, as for a restore, tells nothing more.
            if (stopped) {
                return;
            }
            listener.onBalanceChange(balance);
            listener.onTokenBalances(tokens);
        });
    }

    public void listenAddress(String address, AddressListener listener) {
        addressListeners.put(address, listener);
    }

    public void listenBlockchain(WalletEventListener listener) {
        blockchainListeners.add(listener);
    }

    public String currentAddress() throws Exception {
        WalletServiceGrpc.WalletServiceBlockingStub stub = WalletServiceGrpc.newBlockingStub(this.channel).withCallCredentials(this.creds);
        Api.CurrentAddressRequest request = Api.CurrentAddressRequest.newBuilder().build();
        Api.CurrentAddressResponse reply = stub.currentAddress(request);
        return reply.getAddress();
    }

    /**
     * currentAddresses returns the wallet's current address, plain and in the token-aware form
     * that wallets send CashTokens to.
     */
    public Addresses currentAddresses() throws Exception {
        WalletServiceGrpc.WalletServiceBlockingStub stub = WalletServiceGrpc.newBlockingStub(this.channel).withCallCredentials(this.creds);
        return Addresses.current(tokenAware -> stub.currentAddress(
                Api.CurrentAddressRequest.newBuilder().setTokenAware(tokenAware).build()).getAddress());
    }

    public ListenableFuture<Api.ChangePassphraseResponse> changePasswordAsync(String currentPw, String newPw) throws Exception {
        ByteString bvo = ByteString.copyFromUtf8(currentPw);
        ByteString bvn = ByteString.copyFromUtf8(newPw);
        WalletServiceGrpc.WalletServiceFutureStub stub = WalletServiceGrpc.newFutureStub(this.channel).withCallCredentials(this.creds);
        Api.ChangePassphraseRequest request = Api.ChangePassphraseRequest.newBuilder()
                .setKey(Api.ChangePassphraseRequest.Key.PRIVATE)
                .setOldPassphrase(bvo)
                .setNewPassphrase(bvn)
                .build();
        return stub.changePassphrase(request);
    }

    public Api.CreateTransactionResponse createTransaction(String addr, long amtSatoshi, int feePerByte) {
        WalletServiceGrpc.WalletServiceBlockingStub stub = WalletServiceGrpc.newBlockingStub(this.channel).withCallCredentials(this.creds);
        Api.CreateTransactionRequest.Output output = Api.CreateTransactionRequest.Output.newBuilder().setAmount(amtSatoshi).setAddress(addr).build();
        Api.CreateTransactionRequest request = Api.CreateTransactionRequest.newBuilder()
                .setAccount(0)
                .setRequiredConfirmations(0)
                .setSatPerKbFee(feePerByte*1000)
                .addOutputs(output).build();
        Api.CreateTransactionResponse reply = stub.createTransaction(request);
        return reply;
    }

    /**
     * createTransaction creates a transaction paying the outputs with a fee of satPerKbFee
     * satoshis per kB, a uint32.
     */
    public Api.CreateTransactionResponse createTransaction(List<Api.CreateTransactionRequest.Output> outputs, int satPerKbFee) {
        WalletServiceGrpc.WalletServiceBlockingStub stub = WalletServiceGrpc.newBlockingStub(this.channel).withCallCredentials(this.creds);
        Api.CreateTransactionRequest.Builder builder = Api.CreateTransactionRequest.newBuilder();

        builder.setAccount(0)
        .setRequiredConfirmations(0)
        .setSatPerKbFee(satPerKbFee);

        for (Api.CreateTransactionRequest.Output out : outputs) {
            builder.addOutputs(out);
        }

        Api.CreateTransactionRequest request = builder.build();
        Api.CreateTransactionResponse reply = stub.createTransaction(request);
        return reply;
    }

    /**
     * postPaymentAsync sends the payment of a payment request to the merchant with the protocol,
     * an Api.PaymentProtocol value, that the request was downloaded with.
     */
    public ListenableFuture<Api.PostPaymentResponse> postPaymentAsync(String refundAddress, long refundAmount, byte[] serializedTransaction, String paymentURL, int protocol, byte[] merchantData, String memo) {
        WalletServiceGrpc.WalletServiceFutureStub stub = WalletServiceGrpc.newFutureStub(this.channel).withCallCredentials(this.creds);
        Api.PostPaymentRequest.Output refundOuput = Api.PostPaymentRequest.Output.newBuilder().setAddress(refundAddress).setAmount(refundAmount).build();

        ByteString bs = ByteString.copyFrom(serializedTransaction);
        Api.PostPaymentRequest request = Api.PostPaymentRequest.newBuilder()
                .setRefundOutput(refundOuput)
                .setMerchantData(ByteString.copyFrom(merchantData))
                .setPaymentUrl(paymentURL)
                .setProtocolValue(protocol)
                .addTransactions(bs)
                .build();

        this.memos.put(txidOf(serializedTransaction), memo);

        return stub.postPayment(request);
    }

    public byte[] signTransaction(byte[] serializedTx, List<Long> inputValues, String passphrase) throws Exception {
        WalletServiceGrpc.WalletServiceBlockingStub stub = WalletServiceGrpc.newBlockingStub(this.channel).withCallCredentials(this.creds);
        Api.SignTransactionRequest.Builder builder = Api.SignTransactionRequest.newBuilder();
        builder.setSerializedTransaction(ByteString.copyFrom(serializedTx));
        for (Long val : inputValues) {
            builder.addInputValues(val);
        }
        ByteString pw = ByteString.copyFromUtf8(passphrase);
        builder.setPassphrase(pw);
        Api.SignTransactionRequest request = builder.build();

        Api.SignTransactionResponse reply = stub.signTransaction(request);
        if (reply.getUnsignedInputIndexesList().size() > 0) {
            throw new Exception("Error signing transaction");
        }
        return reply.getTransaction().toByteArray();
    }

    public ListenableFuture<Api.SignTransactionResponse> signTransactionAsync(byte[] serializedTx, List<Long> inputValues, String passphrase) {
        WalletServiceGrpc.WalletServiceFutureStub stub = WalletServiceGrpc.newFutureStub(this.channel).withCallCredentials(this.creds);
        Api.SignTransactionRequest.Builder builder = Api.SignTransactionRequest.newBuilder();
        builder.setSerializedTransaction(ByteString.copyFrom(serializedTx));
        for (Long val : inputValues) {
            builder.addInputValues(val);
        }
        ByteString pw = ByteString.copyFromUtf8(passphrase);
        builder.setPassphrase(pw);
        Api.SignTransactionRequest request = builder.build();

        return stub.signTransaction(request);
    }

    public void publishTransaction(byte[] serializedTransaction, String memo) throws Exception {
        ByteString bs = ByteString.copyFrom(serializedTransaction);
        this.memos.put(txidOf(serializedTransaction), memo);
        WalletServiceGrpc.WalletServiceBlockingStub stub = WalletServiceGrpc.newBlockingStub(this.channel).withCallCredentials(this.creds);
        Api.PublishTransactionRequest request = Api.PublishTransactionRequest.newBuilder().setSignedTransaction(bs).build();
        Api.PublishTransactionResponse reply = stub.publishTransaction(request);
    }

    public ListenableFuture<Api.PublishTransactionResponse> publishTransactionAsync(byte[] serializedTransaction, String memo) {
        ByteString bs = ByteString.copyFrom(serializedTransaction);
        this.memos.put(txidOf(serializedTransaction), memo);
        WalletServiceGrpc.WalletServiceFutureStub stub = WalletServiceGrpc.newFutureStub(this.channel).withCallCredentials(this.creds);
        Api.PublishTransactionRequest request = Api.PublishTransactionRequest.newBuilder().setSignedTransaction(bs).build();
        return stub.publishTransaction(request);
    }

    public Api.SweepAccountResponse sweepAccount(String addr, int feePerByte) throws Exception {
        WalletServiceGrpc.WalletServiceBlockingStub stub = WalletServiceGrpc.newBlockingStub(this.channel).withCallCredentials(this.creds);
        Api.SweepAccountRequest request = Api.SweepAccountRequest.newBuilder()
                .setAccount(0).setSatPerKbFee(feePerByte*1000).setSweepToAddress(addr).build();
        Api.SweepAccountResponse reply = stub.sweepAccount(request);
        return reply;
    }

    public Api.DownloadPaymentRequestResponse downloadPaymentRequest(String uri) throws Exception {
        WalletServiceGrpc.WalletServiceBlockingStub stub = WalletServiceGrpc.newBlockingStub(this.channel).withCallCredentials(this.creds);
        Api.DownloadPaymentRequestRequest request = Api.DownloadPaymentRequestRequest.newBuilder().setUri(uri).build();
        Api.DownloadPaymentRequestResponse reply = stub.downloadPaymentRequest(request);
        return reply;
    }

    /**
     * parsePaymentRequest checks a BIP70 payment request that the app has as a file, as
     * downloadPaymentRequest checks a downloaded one, and returns what that returns.
     */
    public Api.DownloadPaymentRequestResponse parsePaymentRequest(byte[] paymentRequest) throws Exception {
        WalletServiceGrpc.WalletServiceBlockingStub stub = WalletServiceGrpc.newBlockingStub(this.channel).withCallCredentials(this.creds);
        Api.ParsePaymentRequestRequest request = Api.ParsePaymentRequestRequest.newBuilder()
                .setPaymentRequest(ByteString.copyFrom(paymentRequest)).build();
        return stub.parsePaymentRequest(request);
    }

    public long balance() throws Exception {
        WalletServiceGrpc.WalletServiceBlockingStub stub = WalletServiceGrpc.newBlockingStub(this.channel).withCallCredentials(this.creds);
        Api.BalanceRequest request = Api.BalanceRequest.newBuilder().setAccountNumber(0).setRequiredConfirmations(0).build();
        Api.BalanceResponse reply = stub.balance(request);
        return reply.getSpendable();
    }

    /**
     * tokenBalances returns what the wallet holds of each token category, counting unmined
     * transactions as the balance does, in the order the Tokens page lists them.
     */
    public List<TokenBalance> tokenBalances() throws Exception {
        WalletServiceGrpc.WalletServiceBlockingStub stub = WalletServiceGrpc.newBlockingStub(this.channel).withCallCredentials(this.creds);
        Api.TokenBalancesRequest request = Api.TokenBalancesRequest.newBuilder().setAccountNumber(0).setRequiredConfirmations(0).build();
        return TokenBalance.of(stub.tokenBalances(request));
    }

    /**
     * tokenMetadataAsync returns what the wallet can say of the tokens, or fails if it doesn't
     * answer within ten seconds. Asking is what makes a token load, so only ask for the ones that
     * show.
     */
    public ListenableFuture<List<TokenMeta>> tokenMetadataAsync(List<String> categories, boolean nftTypes) {
        return tokenMetadataAsync(categories, nftTypes, false);
    }

    /**
     * peekTokenMetadataAsync returns what the wallet already knows of the tokens, without having
     * them load, for tokens it no longer holds.
     */
    public ListenableFuture<List<TokenMeta>> peekTokenMetadataAsync(List<String> categories, boolean nftTypes) {
        return tokenMetadataAsync(categories, nftTypes, true);
    }

    private ListenableFuture<List<TokenMeta>> tokenMetadataAsync(List<String> categories, boolean nftTypes, boolean peek) {
        WalletServiceGrpc.WalletServiceFutureStub stub = WalletServiceGrpc.newFutureStub(this.channel)
                .withCallCredentials(this.creds).withDeadlineAfter(REPLY_DEADLINE_SECONDS, TimeUnit.SECONDS);
        // A reply that comes after the wallet stopped, as for a switch to another network, says
        // nothing of the tokens of the wallet that runs now.
        return Futures.transform(stub.tokenMetadata(
                Api.TokenMetadataRequest.newBuilder().addAllCategories(categories)
                        .setIncludeNftTypes(nftTypes).setPeek(peek).build()),
                reply -> stopped ? Collections.emptyList() : TokenMeta.of(reply), MoreExecutors.directExecutor());
    }

    /**
     * refreshTokenMetadataAsync has the wallet find a token's metadata, or find it again.
     */
    public ListenableFuture<Api.RefreshTokenMetadataResponse> refreshTokenMetadataAsync(String category) {
        WalletServiceGrpc.WalletServiceFutureStub stub = WalletServiceGrpc.newFutureStub(this.channel)
                .withCallCredentials(this.creds).withDeadlineAfter(REPLY_DEADLINE_SECONDS, TimeUnit.SECONDS);
        return stub.refreshTokenMetadata(
                Api.RefreshTokenMetadataRequest.newBuilder().setCategory(category).build());
    }

    /**
     * networkChangedAsync tells the wallet that the device moved to another network, so that it
     * drops its peers' connections, which can be gone without either side being told, and
     * connects again at once.
     */
    public ListenableFuture<Api.NetworkChangedResponse> networkChangedAsync() {
        WalletServiceGrpc.WalletServiceFutureStub stub = WalletServiceGrpc.newFutureStub(this.channel)
                .withCallCredentials(this.creds).withDeadlineAfter(REPLY_DEADLINE_SECONDS, TimeUnit.SECONDS);
        return stub.networkChanged(Api.NetworkChangedRequest.newBuilder().build());
    }

    /**
     * forgetTokenAsync has the wallet drop all it keeps of a token, as for one the user hid, so
     * that one shown again starts as if never seen.
     */
    public ListenableFuture<Api.ForgetTokenResponse> forgetTokenAsync(String category) {
        WalletServiceGrpc.WalletServiceFutureStub stub = WalletServiceGrpc.newFutureStub(this.channel)
                .withCallCredentials(this.creds).withDeadlineAfter(REPLY_DEADLINE_SECONDS, TimeUnit.SECONDS);
        return stub.forgetToken(Api.ForgetTokenRequest.newBuilder().setCategory(category).build());
    }

    /**
     * tokenIconAsync reads the image at an icon's URI. It has no deadline, as the wallet limits
     * downloads itself, by when they stall, and not by time spent waiting for a gateway.
     */
    public ListenableFuture<byte[]> tokenIconAsync(String uri) {
        return tokenIconAsync(uri, false);
    }

    /**
     * tokenIconAsync reads the image at an icon's URI, ahead of what loads by itself if the user
     * asked for it. If every gateway is busy then, it fails with RESOURCE_EXHAUSTED, which
     * gatewaysBusy tells.
     */
    public ListenableFuture<byte[]> tokenIconAsync(String uri, boolean userAction) {
        WalletServiceGrpc.WalletServiceFutureStub stub = WalletServiceGrpc.newFutureStub(this.channel)
                .withCallCredentials(this.creds);
        Api.TokenIconRequest request = Api.TokenIconRequest.newBuilder().setUri(uri).setUserAction(userAction).build();
        return Futures.transform(stub.tokenIcon(request),
                reply -> reply.getData().toByteArray(), MoreExecutors.directExecutor());
    }

    /**
     * gatewaysBusy returns whether an icon or a Refresh the user asked for failed as every gateway
     * was busy.
     */
    public static boolean gatewaysBusy(Throwable t) {
        return io.grpc.Status.fromThrowable(t).getCode() == io.grpc.Status.Code.RESOURCE_EXHAUSTED;
    }

    /**
     * tokensHeldAsync returns what the wallet holds with CashTokens, which the app doesn't send
     * yet, or fails if the wallet doesn't answer within ten seconds.
     */
    public ListenableFuture<TokensHeld> tokensHeldAsync() {
        WalletServiceGrpc.WalletServiceFutureStub stub = WalletServiceGrpc.newFutureStub(this.channel)
                .withCallCredentials(this.creds).withDeadlineAfter(REPLY_DEADLINE_SECONDS, TimeUnit.SECONDS);
        ListenableFuture<Api.TokenBalancesResponse> tokens = stub.tokenBalances(
                Api.TokenBalancesRequest.newBuilder().setAccountNumber(0).setRequiredConfirmations(0).build());
        ListenableFuture<Api.BalanceResponse> balance = stub.balance(
                Api.BalanceRequest.newBuilder().setAccountNumber(0).setRequiredConfirmations(0).build());
        return Futures.whenAllSucceed(tokens, balance).call(
                () -> TokensHeld.of(Futures.getDone(tokens), Futures.getDone(balance)),
                MoreExecutors.directExecutor());
    }

    /**
     * rescanAsync asks the wallet to scan the blocks from its birthday again, and returns once it
     * has started.
     */
    public ListenableFuture<Api.RescanResponse> rescanAsync() {
        WalletServiceGrpc.WalletServiceFutureStub stub = WalletServiceGrpc.newFutureStub(this.channel).withCallCredentials(this.creds);
        return stub.rescan(Api.RescanRequest.newBuilder().build());
    }

    public boolean validateAddress(String addr) throws Exception {
        WalletServiceGrpc.WalletServiceBlockingStub stub = WalletServiceGrpc.newBlockingStub(this.channel).withCallCredentials(this.creds);
        Api.ValidateAddressRequest request = Api.ValidateAddressRequest.newBuilder().setAddress(addr).build();
        Api.ValidateAddressResponse reply = stub.validateAddress(request);
        return reply.getValid();
    }

    public Api.NetworkResponse network() throws Exception {
        WalletServiceGrpc.WalletServiceBlockingStub stub = WalletServiceGrpc.newBlockingStub(channel).withCallCredentials(creds);
        Api.NetworkRequest request = Api.NetworkRequest.newBuilder().build();
        Api.NetworkResponse reply = stub.network(request);
        return reply;
    }

    private boolean walletExists() throws Exception {
        WalletLoaderServiceGrpc.WalletLoaderServiceBlockingStub stub = WalletLoaderServiceGrpc.newBlockingStub(this.channel).withCallCredentials(this.creds);
        Api.WalletExistsRequest request = Api.WalletExistsRequest.newBuilder().build();
        Api.WalletExistsResponse reply = stub.walletExists(request);
        return reply.getExists();
    }

    private String generateMnemonic() throws Exception {
        WalletLoaderServiceGrpc.WalletLoaderServiceBlockingStub stub = WalletLoaderServiceGrpc.newBlockingStub(this.channel).withCallCredentials(this.creds);
        Api.GenerateMnemonicSeedRequest request = Api.GenerateMnemonicSeedRequest.newBuilder().setBitSize(128).build();
        Api.GenerateMnemonicSeedResponse reply = stub.generateMnemonicSeed(request);
        return reply.getMnemonic();
    }

    /**
     * getTransactions reads all of the wallet's transactions and tells them to the listener. A
     * read that's asked for while one is under way is made once that one's done, for all who
     * asked meanwhile, as notifications can come many at once.
     */
    public void getTransactions(WalletEventListener listener) throws Exception {
        Map<WalletEventListener, Long> replies;
        synchronized (historyLock) {
            if (!reads.start(listener)) {
                return;
            }
            replies = replyNumbers(Collections.singleton(listener));
        }
        readAll(replies);
    }

    // replyNumbers numbers the replies of a read for its listeners, under historyLock, so that a
    // read's replies and the history told meanwhile come in the order they were made.
    private static Map<WalletEventListener, Long> replyNumbers(Collection<WalletEventListener> listeners) {
        Map<WalletEventListener, Long> replies = new LinkedHashMap<>();
        for (WalletEventListener listener : listeners) {
            replies.put(listener, listener.transactionReplies.next());
        }
        return replies;
    }

    // readAll reads all of the wallet's transactions, keeps them, and tells them to the listeners,
    // then makes the read asked for meanwhile, if one was.
    private void readAll(Map<WalletEventListener, Long> replies) {
        WalletServiceGrpc.WalletServiceFutureStub stub = WalletServiceGrpc.newFutureStub(this.channel).withCallCredentials(this.creds);
        // The list needs what the details say of each transaction, not its bytes, which the wallet
        // would serialize again for each one of a long history.
        Api.GetTransactionsRequest request = Api.GetTransactionsRequest.newBuilder()
                .setOmitRawTransactions(true)
                .build();
        ListenableFuture<Api.GetTransactionsResponse> reply = stub.getTransactions(request);

        Futures.addCallback(reply, new FutureCallback<Api.GetTransactionsResponse>() {
            @Override
            public void onSuccess(Api.GetTransactionsResponse result) {
                int bestHeight = 0;
                try {
                    bestHeight = network().getBestHeight();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                List<TransactionData> txs = new ArrayList<TransactionData>();
                for (Api.BlockDetails block : result.getMinedTransactionsList()) {
                    for (Api.TransactionDetails tx : block.getTransactionsList()) {
                        txs.add(extractTransactionData(tx, block.getHeight(), block.getTimestamp()));
                    }
                }
                // An unmined transaction has the time the wallet received it, which stays the
                // same when the list is fetched again.
                for (Api.TransactionDetails tx : result.getUnminedTransactionsList()) {
                    txs.add(extractTransactionData(tx, 0, tx.getTimestamp()));
                }
                // Its own copies, as the listeners set the fiat amounts of theirs.
                List<TransactionData> kept = copiesOf(txs);
                synchronized (historyLock) {
                    history.replace(kept);
                    historyRead = true;
                }
                int height = bestHeight;
                boolean first = true;
                for (Map.Entry<WalletEventListener, Long> replyTo : replies.entrySet()) {
                    WalletEventListener listener = replyTo.getKey();
                    // Each listener has its own, as it sets their fiat amounts.
                    List<TransactionData> told = first ? txs : copiesOf(txs);
                    first = false;
                    listener.transactionReplies.deliver(replyTo.getValue(), () -> {
                        if (!stopped) {
                            listener.onGetTransactions(told, height);
                        }
                    });
                }
                readAllAgain();
            }

            @Override
            public void onFailure(Throwable t) {
                t.printStackTrace();
                // What the read was to bring in is missing, so the next notification reads them
                // all rather than build on them.
                synchronized (historyLock) {
                    historyRead = false;
                }
                readAllAgain();
            }
        }, MoreExecutors.directExecutor());
    }

    // readAllAgain makes the read that was asked for while one was under way, if one was, or ends
    // the reading.
    private void readAllAgain() {
        Map<WalletEventListener, Long> replies;
        synchronized (historyLock) {
            if (stopped) {
                reads.stop();
                return;
            }
            Set<WalletEventListener> next = reads.done();
            if (next.isEmpty()) {
                return;
            }
            replies = replyNumbers(next);
        }
        readAll(replies);
    }

    private static List<TransactionData> copiesOf(List<TransactionData> txs) {
        List<TransactionData> copies = new ArrayList<>(txs.size());
        for (TransactionData tx : txs) {
            copies.add(tx.copy());
        }
        return copies;
    }

    // tellHistory tells the listener the wallet's transactions as the notifications brought them up
    // to date, at once if they weren't told for a while, or else once a while has passed, along
    // with whatever more notifications bring meanwhile.
    private void tellHistory(WalletEventListener listener) {
        synchronized (historyLock) {
            historyFor = listener;
            if (historyToTell) {
                return;
            }
            historyToTell = true;
            long wait = Math.max(0, historyToldAt + HISTORY_GAP_NANOS - System.nanoTime());
            HISTORY.schedule(this::tellHistoryNow, wait, TimeUnit.NANOSECONDS);
        }
    }

    private void tellHistoryNow() {
        WalletEventListener listener;
        List<TransactionData> txs;
        long reply;
        synchronized (historyLock) {
            historyToTell = false;
            historyToldAt = System.nanoTime();
            listener = historyFor;
            historyFor = null;
            if (listener == null || stopped) {
                return;
            }
            // A read of them all that's under way tells them as they are then, and the read after
            // it tells this listener too, as the one under way can be another's.
            if (reads.reading()) {
                reads.start(listener);
                return;
            }
            txs = history.copies();
            reply = listener.transactionReplies.next();
        }
        int bestHeight = 0;
        try {
            bestHeight = network().getBestHeight();
        } catch (Exception e) {
            e.printStackTrace();
        }
        int height = bestHeight;
        listener.transactionReplies.deliver(reply, () -> {
            if (!stopped) {
                listener.onGetTransactions(txs, height);
            }
        });
    }

    /**
     * stopListening stops telling a listener of listenBlockchain about blocks.
     */
    public void stopListening(WalletEventListener listener) {
        blockchainListeners.remove(listener);
    }

    /**
     * listenRescan tells the listener how a rescan goes, until what it returns is run.
     */
    public Runnable listenRescan(WalletEventListener listener) {
        io.grpc.Context.CancellableContext context = io.grpc.Context.current().withCancellation();
        context.run(() -> subscribeRescan(listener));
        return () -> context.cancel(null);
    }

    private void subscribeRescan(WalletEventListener listener) {
        WalletServiceGrpc.WalletServiceStub stub = WalletServiceGrpc.newStub(channel).withCallCredentials(creds);
        Api.RescanNotificationsRequest request = Api.RescanNotificationsRequest.newBuilder().build();
        stub.rescanNotifications(request, new StreamObserver<Api.RescanNotificationsResponse>() {
            @Override
            public void onNext(Api.RescanNotificationsResponse value) {
                onRescanNotification(value, listener);
            }

            @Override
            public void onError(Throwable t) {
                t.printStackTrace();
            }

            @Override
            public void onCompleted() {

            }
        });
    }

    private void onRescanNotification(Api.RescanNotificationsResponse value, WalletEventListener listener) {
        if (value.getFinished()) {
            try {
                Api.NetworkResponse net = network();
                listener.onBlock(net.getBestHeight(), net.getBestBlock());
                getTransactions(listener);
                tellBalances(listener);
                listener.onScanProgress(value.getHeight());
            } catch (Exception e) {
                e.printStackTrace();
            }

        } else {
            // The transactions a rescan finds come in the notifications, and all are read once
            // it's finished.
            try {
                tellBalances(listener);
                listener.onScanProgress(value.getHeight());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private TransactionData extractTransactionData(Api.TransactionDetails tx, int height, long timestamp) {
        String memo = this.memos.get(txidToString(tx.getHash()));
        return transactionData(tx, height, timestamp, memo != null ? memo : "");
    }

    /**
     * transactionData makes the list's entry for one of the wallet's transactions. A transaction
     * it sent names the first address its outputs pay besides the wallet's and how many more they
     * pay, each address once, or that every output pays the wallet. The entry also has how the
     * transaction changed the wallet's tokens, and its spendable balance.
     */
    static TransactionData transactionData(Api.TransactionDetails tx, int height, long timestamp, String memo) {
        long total = 0;
        for (Api.TransactionDetails.Output output : tx.getCreditsList()) {
            total += output.getAmount();
        }
        for (Api.TransactionDetails.Input input : tx.getDebitsList()) {
            total -= input.getPreviousAmount();
        }

        // The outputs with tokens are left out of the spendable balance, and of its change.
        long spendable = 0;
        for (Api.TransactionDetails.Output output : tx.getCreditsList()) {
            if (!output.hasToken()) {
                spendable += output.getAmount();
            }
        }
        for (Api.TransactionDetails.Input input : tx.getDebitsList()) {
            if (!input.hasPreviousToken()) {
                spendable -= input.getPreviousAmount();
            }
        }

        String txid = txidToString(tx.getHash());
        TransactionData txData = new TransactionData(txid, (total > 0), memo, total, "", "", timestamp, "", height);
        txData.setTokens(TokenChange.of(tx));
        txData.setSpendableAmount(spendable);
        if (total <= 0) {
            // An output that pays no single address, such as OP_RETURN data, has none.
            Set<String> addresses = new LinkedHashSet<>();
            for (Api.TransactionDetails.OtherOutput output : tx.getOtherOutputsList()) {
                if (!output.getAddress().isEmpty()) {
                    addresses.add(output.getAddress());
                }
            }
            String first = addresses.isEmpty() ? "" : addresses.iterator().next();
            txData.setRecipients(first, Math.max(addresses.size() - 1, 0), tx.getOtherOutputsCount() == 0);
        }
        return txData;
    }

    // txidToString writes a transaction hash, in the byte order of the wire, as a transaction ID.
    static String txidToString(ByteString txid) {
        byte[] txBytes = txid.toByteArray();
        reverse(txBytes);
        return BaseEncoding.base16().lowerCase().encode(txBytes);
    }

    /**
     * txidOf returns the ID of a serialized transaction, its double SHA-256 as txidToString writes
     * it, which keys the memos of the transactions the app sends.
     */
    static String txidOf(byte[] serializedTransaction) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha256.digest(sha256.digest(serializedTransaction));
            return txidToString(ByteString.copyFrom(hash));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("No SHA-256", e);
        }
    }

    /**
     * Start will start the bchwallet daemon with the requested config options. It returns right
     * away, and the daemon starts once any earlier wallet has shut down.
     */
    public void start() {
        String configPath = this.getConfigFilePath;
        started = lifecycle.submit(() -> mobile.Mobile.startWallet(configPath));
    }

    /**
     * Stop cleanly shuts down the wallet daemon. This must be called on close to guarantee
     * no data is corrupted. It returns right away and the daemon shuts down in the background.
     * Once stopped the wallet can't be used again; create and start a new one instead.
     */
    public synchronized void stop() {
        // Only the first call stops the daemon, so that a wallet stopped
        // earlier can't stop the daemon of a wallet started after it.
        if (stopped) {
            return;
        }
        stopped = true;
        running = false;
        if (instance == this) {
            instance = null;
        }
        if (streams != null) {
            streams.stop();
        }
        ManagedChannel channel = this.channel;
        lifecycle.execute(() -> {
            channel.shutdownNow();
            mobile.Mobile.stopWallet();
        });
    }

    public static void reverse(byte[] array) {
        if (array == null) {
            return;
        }
        int i = 0;
        int j = array.length - 1;
        byte tmp;
        while (j > i) {
            tmp = array[j];
            array[j] = array[i];
            array[i] = tmp;
            j--;
            i++;
        }
    }

    public static String SHA256 (String text) throws NoSuchAlgorithmException {

        MessageDigest md = MessageDigest.getInstance("SHA-256");

        md.update(text.getBytes());
        byte[] digest = md.digest();

        return Base64.encodeToString(digest, Base64.DEFAULT);
    }
}
