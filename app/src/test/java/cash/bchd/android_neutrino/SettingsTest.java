package cash.bchd.android_neutrino;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

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
}
