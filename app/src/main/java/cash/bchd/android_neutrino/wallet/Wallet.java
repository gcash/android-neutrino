package cash.bchd.android_neutrino.wallet;

import android.content.Context;

import com.google.protobuf.ByteString;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import walletrpc.Api;
import walletrpc.WalletLoaderServiceGrpc;

/**
 * Wallet represents an instance of a bchwallet. It will load and start the
 * bchwallet daemon and provide convience methods to the wallet's API calls.
 */
public class Wallet {

    private String configFilePath;
    private final String host = "127.0.0.1";
    private final int port = 8332;
    private ManagedChannel channel;
    public static final io.grpc.Context.Key<String> AUTH_TOKEN_KEY = io.grpc.Context.key("AuthenticationToken");
    private AuthCredentials creds;

    private final String DEFAULT_PASSPHRASE = "LETMEIN";

    /**
     * The wallet constructor takes in a context which it uses to derive the config file
     * path and appdatadir.
     */
    public Wallet(Context context, Config config) {
        this.configFilePath = config.getConfigFilePath();
        this.creds = new AuthCredentials(config.getAuthToken());
        this.channel = ManagedChannelBuilder.forAddress(this.host, this.port).usePlaintext().build();
        try {
            config.save(context);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean walletExists() throws Exception {
        WalletLoaderServiceGrpc.WalletLoaderServiceBlockingStub stub = WalletLoaderServiceGrpc.newBlockingStub(this.channel).withCallCredentials(this.creds);
        Api.WalletExistsRequest request = Api.WalletExistsRequest.newBuilder().build();
        Api.WalletExistsResponse reply = stub.walletExists(request);
        return reply.getExists();
    }

    public String createWallet() throws Exception {
        String mnemonic = generateMnemonic();

        WalletLoaderServiceGrpc.WalletLoaderServiceBlockingStub stub = WalletLoaderServiceGrpc.newBlockingStub(this.channel).withCallCredentials(this.creds);
        ByteString pw = ByteString.copyFromUtf8(DEFAULT_PASSPHRASE);
        Api.CreateWalletRequest request = Api.CreateWalletRequest.newBuilder().setPrivatePassphrase(pw).setMnemonicSeed(mnemonic).build();
        Api.CreateWalletResponse reply = stub.createWallet(request);
        return mnemonic;
    }

    private String generateMnemonic() throws Exception {
        WalletLoaderServiceGrpc.WalletLoaderServiceBlockingStub stub = WalletLoaderServiceGrpc.newBlockingStub(this.channel).withCallCredentials(this.creds);
        Api.GenerateMnemonicSeedRequest request = Api.GenerateMnemonicSeedRequest.newBuilder().setBitSize(128).build();
        Api.GenerateMnemonicSeedResponse reply = stub.generateMnemonicSeed(request);
        return reply.getMnemonic();
    }

    /**
     * Start will start the bchwallet daemon with the requested config options
     */
    public void start() {
        mobile.Mobile.startWallet(this.configFilePath);
    }

    /**
     * Stop cleanly shuts down the wallet daemon. This must be called on close to guarantee
     * no data is corrupted.
     */
    public void stop() {
        mobile.Mobile.stopWallet();
    }
}
