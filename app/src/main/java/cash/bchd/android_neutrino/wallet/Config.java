package cash.bchd.android_neutrino.wallet;

import android.content.Context;

import java.io.FileOutputStream;
import java.security.SecureRandom;
import com.google.common.io.BaseEncoding;

/**
 * Config represents the bchwallet config options to be set on startup.
 */
public class Config {

    // The filename for the config file
    private static final String CONFIG_FILE_NAME = "bchwallet.conf";

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
    private String caFilePath;

    // The app data directory
    private String dataDir;

    // The authentication token to use with the gRPC API
    private String authToken;

    /**
     * Construct the config file.
     */
    public Config(String dataDir, Boolean useSPV, Boolean blocksOnly, String[] connect, String rpcConnect,
                  String bchdUsername, String bchdPassword, String caFilePath) {
        this.dataDir = dataDir;
        this.useSPV = useSPV;
        this.blocksOnly = blocksOnly;
        this.connect = connect;
        this.rpcConnect = rpcConnect;
        this.bchdUsername = bchdUsername;
        this.bchdPassword = bchdPassword;
        this.caFilePath = caFilePath;

        SecureRandom random = new SecureRandom();
        byte randomBytes[] = new byte[32];
        random.nextBytes(randomBytes);

        this.authToken = BaseEncoding.base32Hex().lowerCase().encode(randomBytes);
    }

    /**
     * Build the config file as a string and return it
     */
    public String getConfigData() {
        String configFileContents = "[Application Options]\n\nappdata="+this.dataDir+"\nlogdir="+ this.dataDir+
                "/logs\nexperimentalrpclisten=127.0.0.1\nnoinitialload=1\nnoservertls=1\nauthtoken="+ this.authToken+ "\n";
        if (this.useSPV) {
            configFileContents += "usespv=1\n";
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
}
