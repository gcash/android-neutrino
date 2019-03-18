package cash.bchd.android_neutrino.wallet;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import com.google.common.io.BaseEncoding;

/**
 * Config represents the bchwallet config options to be set on startup.
 */
public class Config {

    // The filename for the config file
    private static final String CONFIG_FILE_NAME = "bchwallet.conf";

    // The filename for the bchd cert
    private static final String CERT_FILE_NAME = "rpc.cert";

    // This is to determine whether or not we should start the wallet or block startup so we can
    // create the wallet.
    private Boolean noInitialLoad;

    // Enables the experimental use of SPV rather than RPC for chain synchronization
    private Boolean useSPV;

    // Disables downloading unconfirmed transaction when in SPV mode
    private Boolean blocksOnly;

    // Connect only to the specified peers at startup
    private String[] connect;

    // The following are configuration parameters for connecting to a bchd full node
    // over the RPC interface. These must provided if useSPV is false.
    private String rpcConnect;
    private String bchdUsername;
    private String bchdPassword;
    private String cert;
    private String caFilePath;

    // The app data directory
    private String dataDir;

    // The authentication token to use with the gRPC API
    private String authToken;

    /**
     * Construct the config file.
     */
    public Config(String dataDir, Boolean noInitialLoad, Boolean useSPV, Boolean blocksOnly, String[] connect, String rpcConnect,
                  String bchdUsername, String bchdPassword, String cert) {
        this.dataDir = dataDir;
        this.useSPV = useSPV;
        this.blocksOnly = blocksOnly;
        this.connect = connect;
        this.rpcConnect = rpcConnect;
        this.bchdUsername = bchdUsername;
        this.bchdPassword = bchdPassword;
        this.noInitialLoad = noInitialLoad;
        this.caFilePath = "";
        this.cert = cert + "\n";

        if (!cert.equals("")) {
            this.caFilePath = this.dataDir + "/files/" + this.CERT_FILE_NAME;
        }

        SecureRandom random = new SecureRandom();
        byte randomBytes[] = new byte[32];
        random.nextBytes(randomBytes);

        this.authToken = BaseEncoding.base16().lowerCase().encode(randomBytes);
    }

    /**
     * Build the config file as a string and return it
     */
    public String getConfigData() {
        String configFileContents = "[Application Options]\n\nappdata="+this.dataDir+"\nlogdir="+ this.dataDir+
                "/logs\nexperimentalrpclisten=127.0.0.1\nnoservertls=1\nauthtoken="+ this.authToken+ "\n";

        if (this.noInitialLoad) {
            configFileContents += "noinitialload=1\n";
        }
        if (this.useSPV) {
            configFileContents += "usespv=1\n";
            configFileContents += "connect=35.202.172.160:8333\n";
        }
        if (this.blocksOnly) {
            configFileContents += "blocksonly=1\n";
        }
        StringBuilder sb = new StringBuilder();
        for (String c : this.connect) {
            sb.append("connect=" + c + "\n");
        }
        if (!sb.toString().equals("")) {
            configFileContents += sb.toString();
        }
        if (!this.rpcConnect.equals("")) {
            configFileContents += "rpcconnect=" + this.rpcConnect + "\n";
        }
        if (!this.bchdUsername.equals("")) {
            configFileContents += "bchdusername=" + this.bchdUsername + "\n";
        }
        if (!this.bchdPassword.equals("")) {
            configFileContents += "bchdpassword=" + this.bchdPassword + "\n";
        }
        if (!this.caFilePath.equals("")) {
            configFileContents += "cafile=" + this.caFilePath + "\n";
        }
        return configFileContents;
    }

    /**
     * Save the config file to the data directory
     */
    public void save(Context context) throws Exception {
        if (!this.caFilePath.equals("")) {
            FileOutputStream certOutputSgtream = context.openFileOutput(this.CERT_FILE_NAME, Context.MODE_PRIVATE);
            certOutputSgtream.write(this.cert.getBytes());
            certOutputSgtream.close();
        }

        FileOutputStream outputStream = context.openFileOutput(this.CONFIG_FILE_NAME, Context.MODE_PRIVATE);
        outputStream.write(this.getConfigData().getBytes());
        outputStream.close();
    }

    public String getConfigFilePath() {
        return this.dataDir + "/files/" + this.CONFIG_FILE_NAME;
    }

    public String getAuthToken() {
        return this.authToken;
    }

    public String getDataDir() {
        return this.dataDir;
    }

    public String getCaFilePath() {
        return this.caFilePath;
    }

    public String getCertificate() {
        return this.cert;
    }

    public String getBchdUsername() {
        return this.bchdUsername;
    }

    public String getBchdPassword() {
        return this.bchdPassword;
    }

    public String getRpcConnect() {
        return this.rpcConnect;
    }
}
