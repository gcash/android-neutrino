package cash.bchd.android_neutrino.wallet;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConfigTest {

    private static Config config(Network network) {
        return new Config("/data/app", network, false, true, false, new String[0], new String[0],
                "", "", "", "", 1600000000L);
    }

    @Test
    public void selectsTestnet4() {
        assertTrue(config(Network.TESTNET4).getConfigData().contains("\ntestnet4=1\n"));
    }

    @Test
    public void selectsNoNetworkOnMainnet() {
        // bchwallet runs on mainnet unless its config selects another network.
        String data = config(Network.MAINNET).getConfigData();
        assertFalse(data.contains("testnet"));
        assertFalse(data.contains("chipnet"));
    }

    @Test
    public void limitsTheNftTypes() {
        Config config = config(Network.MAINNET);
        assertTrue(config.getConfigData().contains("\nbcmrnftlimit=1024\n"));
        config.setTokenMetadata("", "", new String[0], true, 0);
        assertTrue(config.getConfigData().contains("\nbcmrnftlimit=0\n"));
    }

    @Test
    public void servesProfilesOnlyWhenAsked() {
        Config config = config(Network.MAINNET);
        assertFalse(config.getConfigData().contains("profile="));
        config.setProfile("127.0.0.1:6060");
        assertTrue(config.getConfigData().contains("\nprofile=127.0.0.1:6060\n"));
    }
}
