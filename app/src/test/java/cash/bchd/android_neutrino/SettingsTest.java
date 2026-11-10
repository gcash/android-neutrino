package cash.bchd.android_neutrino;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import cash.bchd.android_neutrino.wallet.Network;
import cash.bchd.android_neutrino.wallet.TokenBalance;
import cash.bchd.android_neutrino.wallet.TokenNft;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
        List<TokenBalance> tokens = Collections.singletonList(new TokenBalance("aa", 5, 1, 0,
                Collections.singletonList(new TokenNft(TokenNft.Capability.MINTING, "01", "bb", 0, 1000))));
        mainnet.setLastTokens(tokens);
        mainnet.hideToken("aa");
        mainnet.hideToken("bb");
        mainnet.setWalletInitialized(true);

        Settings testnet4 = new Settings(appPrefs, testnet4Prefs, Network.TESTNET4);
        assertEquals("", testnet4.getPeers());
        assertEquals(EncryptionType.UNENCRYPTED, testnet4.getEncryptionType());
        assertEquals(0, testnet4.getLastBalance());
        assertEquals(Collections.emptyList(), testnet4.getLastTokens());
        assertEquals(Collections.emptySet(), testnet4.getHiddenTokens());
        assertEquals(false, testnet4.getWalletInitialized());
        testnet4.setPeers("35.208.255.10");
        testnet4.setLastBalance(7);

        assertEquals("35.208.165.58", mainnet.getPeers());
        assertEquals(5000, mainnet.getLastBalance());
        assertEquals(tokens, mainnet.getLastTokens());
        assertEquals(new HashSet<>(Arrays.asList("aa", "bb")), mainnet.getHiddenTokens());
        assertEquals("35.208.255.10", testnet4.getPeers());
    }

    @Test
    public void forgetsTheAddressItOnceKept() {
        InMemoryPreferences prefs = new InMemoryPreferences();
        prefs.edit().putString("LastAddress", "qpm2qsznhks23z7629mms6s4cwef74vcwvrqekrq9w")
                .putString("LastTokenAddress", "zpm2qsznhks23z7629mms6s4cwef74vcwvrqekrq9w")
                .putLong("LastBalance", 5000).apply();
        Settings settings = new Settings(prefs, prefs, Network.MAINNET);
        assertFalse(prefs.contains("LastAddress"));
        assertFalse(prefs.contains("LastTokenAddress"));
        assertEquals(5000, settings.getLastBalance());
    }

    @Test
    public void showsAHiddenTokenAgain() {
        Settings settings = new Settings(new InMemoryPreferences(), new InMemoryPreferences(), Network.TESTNET4);
        settings.hideToken("aa");
        settings.hideToken("bb");
        settings.showToken("aa");
        assertEquals(Collections.singleton("bb"), settings.getHiddenTokens());
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

    @org.junit.Test
    public void gatewaysAreHttpsHostsOnly() {
        String[] gateways = Settings.parseGateways(
                "https://a.example, https://b.example:8443/\nhttp://c.example ftp://d.example https://e.example/path");
        org.junit.Assert.assertArrayEquals(
                new String[]{"https://a.example", "https://b.example:8443"}, gateways);
    }

    @Test
    public void gatewaysForgiveEasyMistakes() {
        org.junit.Assert.assertArrayEquals(
                new String[]{"https://ipfs.io", "https://a.example", "https://b.example", "https://c.example:8443"},
                Settings.parseGateways("  ipfs.io ,, HTTPS://A.example/ipfs/\nb.example/IPFS c.example:8443/ipfs"));
    }

    @Test
    public void gatewaysKeepEachHostOnce() {
        org.junit.Assert.assertArrayEquals(
                new String[]{"https://ipfs.io", "https://dweb.link"},
                Settings.parseGateways("ipfs.io https://ipfs.io/ HTTPS://IPFS.IO/ipfs dweb.link"));
    }

    @Test
    public void gatewaysRefuseWhatIsNoHttpsHost() {
        org.junit.Assert.assertEquals(0, Settings.parseGateways(
                "http://a.example ftp://b.example https://c.example/path d.example/ipfs/x notahost e.example:port "
                        + "https:// user@f.example").length);
        org.junit.Assert.assertEquals(0, Settings.parseGateways("").length);
        org.junit.Assert.assertEquals(0, Settings.parseGateways("   ").length);
    }

    @Test
    public void theGatewaysTheAppComesWithStartWithTheFastest() {
        Settings settings = new Settings(new InMemoryPreferences(), new InMemoryPreferences(), Network.MAINNET);
        org.junit.Assert.assertArrayEquals(new String[]{"https://ipfs.filebase.io", "https://ipfs.orbitor.dev",
                "https://gateway.pinata.cloud", "https://ipfs.io", "https://dweb.link"}, settings.getIpfsGateways());
    }

    @Test
    public void listsHiddenTokensNewestFirst() {
        InMemoryPreferences prefs = new InMemoryPreferences();
        // Hidden before the order was kept.
        prefs.edit().putStringSet("HiddenTokens", new HashSet<>(Arrays.asList("cc", "aa"))).apply();
        Settings settings = new Settings(prefs, prefs, Network.MAINNET);
        assertEquals(Arrays.asList("aa", "cc"), settings.getHiddenTokensNewestFirst());
        settings.hideToken("dd");
        settings.hideToken("bb");
        assertEquals(Arrays.asList("bb", "dd", "aa", "cc"), settings.getHiddenTokensNewestFirst());
        settings.hideToken("dd");
        assertEquals(Arrays.asList("dd", "bb", "aa", "cc"), settings.getHiddenTokensNewestFirst());
        settings.showToken("bb");
        settings.showToken("aa");
        assertEquals(Arrays.asList("dd", "cc"), settings.getHiddenTokensNewestFirst());
    }

    @Test
    public void remembersTheHiddenTokensToForget() {
        Settings settings = new Settings(new InMemoryPreferences(), new InMemoryPreferences(), Network.TESTNET4);
        settings.hideToken("aa");
        settings.hideToken("bb");
        settings.hideToken("cc");
        settings.showToken("aa");
        settings.forgotToken("bb");
        assertEquals(Collections.singleton("cc"), settings.getTokensToForget());
        assertEquals(new java.util.HashSet<>(Arrays.asList("bb", "cc")), settings.getHiddenTokens());
    }
}
