package cash.bchd.android_neutrino;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LocalNetworkTest {

    @Test
    public void tellsLocalAddressesFromOthers() {
        for (String host : new String[]{"10.0.2.2", "172.16.0.1", "172.31.255.255", "192.168.1.10",
                "169.254.1.1", "100.64.0.1", "100.127.255.254", "224.0.0.251", "255.255.255.255",
                "fe80::1", "FE80::abcd", "fec0::1", "fd12:3456::1", "fc00::1", "ff02::fb",
                "::ffff:192.168.0.1", "mynode.local", "MyNode.Local."}) {
            assertTrue(host, LocalNetwork.isLocal(host));
        }
        for (String host : new String[]{"35.208.255.10", "8.8.8.8", "172.32.0.1", "172.15.0.1",
                "100.128.0.1", "127.0.0.1", "::1", "2001:db8::1", "::ffff:8.8.8.8",
                "seed.bchd.cash", "localhost", "192.168.1", "192.168.1.256", "fe80:zz::1", ""}) {
            assertFalse(host, LocalNetwork.isLocal(host));
        }
    }

    @Test
    public void takesTheHostOfANode() {
        assertEquals("192.168.1.10", LocalNetwork.host("192.168.1.10:8335"));
        assertEquals("192.168.1.10", LocalNetwork.host(" 192.168.1.10 "));
        assertEquals("fe80::1", LocalNetwork.host("[fe80::1]:8333"));
        assertEquals("fe80::1", LocalNetwork.host("fe80::1"));
        assertEquals("seed.bchd.cash", LocalNetwork.host("seed.bchd.cash:8333"));
    }

    @Test
    public void listsTheLocalNodes() {
        assertEquals(Arrays.asList("192.168.1.10:8335", "[fd00::2]:8333"), LocalNetwork.local(
                Arrays.asList("35.208.255.10:28333", "192.168.1.10:8335", "seed.bchd.cash", "[fd00::2]:8333")));
        assertEquals(Collections.emptyList(), LocalNetwork.local(Collections.emptyList()));
    }
}
