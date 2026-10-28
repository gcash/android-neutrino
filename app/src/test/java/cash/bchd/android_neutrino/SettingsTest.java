package cash.bchd.android_neutrino;

import org.junit.Test;

import cash.bchd.android_neutrino.wallet.Network;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class SettingsTest {

    @Test
    public void parsesPeers() {
        assertArrayEquals(new String[]{"node.example.com", "1.2.3.4:8333", "[2001:db8::1]:8333"},
                Settings.parsePeers(" node.example.com, 1.2.3.4:8333 [2001:db8::1]:8333,"));
        assertArrayEquals(new String[0], Settings.parsePeers(""));
    }

    @Test
    public void leavesOutPeersThatWouldChangeTheConfigFile() {
        assertArrayEquals(new String[]{"1.2.3.4"},
                Settings.parsePeers("1.2.3.4,rpcconnect=evil.example.com"));
    }

    @Test
    public void keepsEachNetworksWalletApart() {
        InMemoryPreferences appPrefs = new InMemoryPreferences();
        InMemoryPreferences testnet4Prefs = new InMemoryPreferences();
        Settings mainnet = new Settings(appPrefs, appPrefs, Network.MAINNET);
        mainnet.setPeers("35.208.165.58");
        mainnet.setEncryptionType(EncryptionType.PIN);
        mainnet.setLastBalance(5000);
        mainnet.setWalletInitialized(true);

        Settings testnet4 = new Settings(appPrefs, testnet4Prefs, Network.TESTNET4);
        assertEquals("", testnet4.getPeers());
        assertEquals(EncryptionType.UNENCRYPTED, testnet4.getEncryptionType());
        assertEquals(0, testnet4.getLastBalance());
        assertEquals(false, testnet4.getWalletInitialized());
        testnet4.setPeers("35.208.255.10");
        testnet4.setLastBalance(7);

        assertEquals("35.208.165.58", mainnet.getPeers());
        assertEquals(5000, mainnet.getLastBalance());
        assertEquals("35.208.255.10", testnet4.getPeers());
    }

    @Test
    public void sharesTheAppsSettingsOnEveryNetwork() {
        InMemoryPreferences appPrefs = new InMemoryPreferences();
        Settings mainnet = new Settings(appPrefs, appPrefs, Network.MAINNET);
        Settings testnet4 = new Settings(appPrefs, new InMemoryPreferences(), Network.TESTNET4);
        testnet4.setFeePerByte(3);
        testnet4.setFiatCurrency("eur");
        assertEquals(3, mainnet.getFeePerByte());
        assertEquals("eur", mainnet.getFiatCurrency());

        // The data migration ran once for the app, and must not run again on another network.
        mainnet.setRepoVersion(3);
        assertEquals(3, testnet4.getRepoVersion());
    }

    @Test
    public void savesTheNetworkToSwitchTo() {
        InMemoryPreferences appPrefs = new InMemoryPreferences();
        Settings mainnet = new Settings(appPrefs, appPrefs, Network.MAINNET);
        mainnet.setNetwork(Network.TESTNET4);
        assertEquals("testnet4", appPrefs.getString("Network", ""));
        // The settings stay those of the network they were loaded for, until the app loads them
        // again for the other one.
        assertEquals(Network.MAINNET, mainnet.getNetwork());
    }

    @Test
    public void keepsTheNamesOfMainnetsFiles() {
        // Earlier versions kept mainnet's transactions in this file, and its settings in the
        // default preferences.
        assertEquals("transactionData.dat", TransactionStore.fileName(Network.MAINNET));
        assertNotEquals(TransactionStore.fileName(Network.MAINNET), TransactionStore.fileName(Network.TESTNET4));
        assertEquals("wallet_testnet4", Settings.walletPrefsName(Network.TESTNET4));
    }
}
