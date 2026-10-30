package cash.bchd.android_neutrino.wallet;

import android.content.Context;
import android.util.Base64;

import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;

import java.io.File;
import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
    private ManagedChannel channel;
    public static final io.grpc.Context.Key<String> AUTH_TOKEN_KEY = io.grpc.Context.key("AuthenticationToken");
    private AuthCredentials creds;

    public final static String DEFAULT_PASSPHRASE = "LETMEIN";

    private HashMap<String, AddressListener> addressListeners = new HashMap<String, AddressListener>();
    // The memos of the transactions the app sent, which the wallet doesn't have, by transaction.
    private HashMap<ByteString, String> memos = new HashMap<ByteString, String>();
    private List<WalletEventListener> blockchainListeners = new ArrayList<WalletEventListener>();

    private int lastBlockHeight;
    private String lastBlockHash;

    private static boolean running = false;

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

    /**
     * The wallet constructor takes in a context which it uses to derive the config file
     * path and appdatadir.
     */
    public Wallet(Context context, Config config) {
        this.config = config;
        this.getConfigFilePath = config.getConfigFilePath();
        this.creds = new AuthCredentials(config.getAuthToken());
        this.channel = ManagedChannelBuilder.forAddress(this.host, config.getRpcPort()).usePlaintext().build();
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
                listener.onBalanceChange(balance());
                getTransactions(listener);
                listenTransactions(listener);
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

    public void listenTransactions(WalletEventListener listener) {
        try {
            Api.NetworkResponse network = network();
            lastBlockHeight = network.getBestHeight();
            lastBlockHash = network.getBestBlock();

            listener.onBlock(lastBlockHeight, lastBlockHash);
        } catch (Exception e) {
            e.printStackTrace();
        }
        blockchainListeners.add(listener);

        listenRescan(listener);

        WalletServiceGrpc.WalletServiceStub stub = WalletServiceGrpc.newStub(channel).withCallCredentials(creds);
        Api.TransactionNotificationsRequest request = Api.TransactionNotificationsRequest.newBuilder().build();
        stub.transactionNotifications(request, new StreamObserver<Api.TransactionNotificationsResponse>() {
            @Override
            public void onNext(Api.TransactionNotificationsResponse value) {
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

                if (hasMinedTransactions || hasUnMinedTransactions) {
                    try {
                        long bal = balance();
                        listener.onBalanceChange(bal);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                // The listener rebuilds its list from all of the wallet's transactions, which
                // also drops the ones the wallet removed and moves the ones a reorg detached.
                if (hasMinedTransactions || hasUnMinedTransactions || value.getDetachedBlocksCount() > 0) {
                    try {
                        getTransactions(listener);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
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

        this.memos.put(bs, memo);

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
        this.memos.put(bs, memo);
        WalletServiceGrpc.WalletServiceBlockingStub stub = WalletServiceGrpc.newBlockingStub(this.channel).withCallCredentials(this.creds);
        Api.PublishTransactionRequest request = Api.PublishTransactionRequest.newBuilder().setSignedTransaction(bs).build();
        Api.PublishTransactionResponse reply = stub.publishTransaction(request);
    }

    public ListenableFuture<Api.PublishTransactionResponse> publishTransactionAsync(byte[] serializedTransaction, String memo) {
        ByteString bs = ByteString.copyFrom(serializedTransaction);
        this.memos.put(bs, memo);
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

    public void getTransactions(WalletEventListener listener) throws Exception {
        WalletServiceGrpc.WalletServiceFutureStub stub = WalletServiceGrpc.newFutureStub(this.channel).withCallCredentials(this.creds);
        Api.GetTransactionsRequest request = Api.GetTransactionsRequest.newBuilder().build();
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
                listener.onGetTransactions(txs, bestHeight);
            }

            @Override
            public void onFailure(Throwable t) {
                t.printStackTrace();
            }
        }, MoreExecutors.directExecutor());
    }

    public void listenRescan(WalletEventListener listener) {
        WalletServiceGrpc.WalletServiceStub stub = WalletServiceGrpc.newStub(channel).withCallCredentials(creds);
        Api.RescanNotificationsRequest request = Api.RescanNotificationsRequest.newBuilder().build();
        stub.rescanNotifications(request, new StreamObserver<Api.RescanNotificationsResponse>() {
            @Override
            public void onNext(Api.RescanNotificationsResponse value) {
                if (value.getFinished()) {
                   try {
                       Api.NetworkResponse net = network();
                       listener.onBlock(net.getBestHeight(), net.getBestBlock());
                       getTransactions(listener);
                       listener.onBalanceChange(balance());
                       listener.onScanProgress(value.getHeight());
                   } catch (Exception e) {
                       e.printStackTrace();
                   }

                } else {
                    try {
                        getTransactions(listener);
                        listener.onBalanceChange(balance());
                        listener.onScanProgress(value.getHeight());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
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

    private TransactionData extractTransactionData(Api.TransactionDetails tx, int height, long timestamp) {
        String memo = this.memos.get(tx.getTransaction());
        return transactionData(tx, height, timestamp, memo != null ? memo : "");
    }

    /**
     * transactionData makes the list's entry for one of the wallet's transactions. A transaction
     * it sent names the first address its outputs pay besides the wallet's and how many more they
     * pay, each address once, or that every output pays the wallet.
     */
    static TransactionData transactionData(Api.TransactionDetails tx, int height, long timestamp, String memo) {
        long total = 0;
        for (Api.TransactionDetails.Output output : tx.getCreditsList()) {
            total += output.getAmount();
        }
        for (Api.TransactionDetails.Input input : tx.getDebitsList()) {
            total -= input.getPreviousAmount();
        }

        String txid = txidToString(tx.getHash());
        TransactionData txData = new TransactionData(txid, (total > 0), memo, total, "", "", timestamp, "", height);
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

    private static String txidToString(ByteString txid) {
        byte[] txBytes = txid.toByteArray();
        reverse(txBytes);
        return BaseEncoding.base16().lowerCase().encode(txBytes);
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
