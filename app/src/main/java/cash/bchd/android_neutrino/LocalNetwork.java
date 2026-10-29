package cash.bchd.android_neutrino;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * LocalNetwork tells which of the nodes that the wallet is set to connect to are on the local
 * network, which apps targeting Android 17 reach only with the user's permission. Without it,
 * their connections time out.
 */
final class LocalNetwork {

    private LocalNetwork() {
    }

    /**
     * local returns the nodes, each a host or host:port, that are on the local network: an
     * address of a private, shared, link-local or multicast range, or a .local name, which the
     * phone looks up on the local network. Telling whether another name is would take a DNS
     * lookup, so such names are left out.
     */
    static List<String> local(List<String> nodes) {
        List<String> local = new ArrayList<>();
        for (String node : nodes) {
            if (isLocal(host(node))) {
                local.add(node);
            }
        }
        return local;
    }

    /**
     * host returns the host of a host or host:port, without the brackets of an IPv6 address.
     */
    static String host(String node) {
        String s = node.trim();
        if (s.startsWith("[")) {
            int end = s.indexOf(']');
            return end < 0 ? s.substring(1) : s.substring(1, end);
        }
        int colon = s.indexOf(':');
        // An IPv6 address without brackets has no port.
        if (colon >= 0 && colon == s.lastIndexOf(':')) {
            return s.substring(0, colon);
        }
        return s;
    }

    static boolean isLocal(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        if (h.endsWith(".")) {
            h = h.substring(0, h.length() - 1);
        }
        if (h.endsWith(".local")) {
            return true;
        }
        int[] v4 = ipv4(h);
        if (v4 != null) {
            return isLocal(v4);
        }
        if (h.startsWith("::ffff:")) {
            v4 = ipv4(h.substring("::ffff:".length()));
            return v4 != null && isLocal(v4);
        }
        if (!isIpv6(h)) {
            return false;
        }
        String group = h.substring(0, h.indexOf(':'));
        int first = group.isEmpty() ? 0 : Integer.parseInt(group, 16);
        return (first & 0xffc0) == 0xfe80                   // fe80::/10, link-local
                || (first & 0xffc0) == 0xfec0               // fec0::/10, site-local
                || (first & 0xfe00) == 0xfc00               // fc00::/7, unique local
                || (first & 0xff00) == 0xff00;              // ff00::/8, multicast
    }

    private static boolean isLocal(int[] a) {
        return a[0] == 10                                   // 10.0.0.0/8
                || a[0] == 172 && (a[1] & 0xf0) == 16       // 172.16.0.0/12
                || a[0] == 192 && a[1] == 168               // 192.168.0.0/16
                || a[0] == 169 && a[1] == 254               // 169.254.0.0/16, link-local
                || a[0] == 100 && (a[1] & 0xc0) == 64       // 100.64.0.0/10, shared
                || (a[0] & 0xf0) == 224                     // 224.0.0.0/4, multicast
                || a[0] == 255 && a[1] == 255 && a[2] == 255 && a[3] == 255;
    }

    // ipv4 returns the bytes of a dotted-decimal IPv4 address, or null for anything else.
    private static int[] ipv4(String s) {
        String[] parts = s.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        int[] bytes = new int[4];
        for (int i = 0; i < 4; i++) {
            if (!parts[i].matches("[0-9]{1,3}")) {
                return null;
            }
            bytes[i] = Integer.parseInt(parts[i]);
            if (bytes[i] > 255) {
                return null;
            }
        }
        return bytes;
    }

    // isIpv6 returns whether s, in lower case, is an IPv6 address in one of its text forms.
    private static boolean isIpv6(String s) {
        int last = s.lastIndexOf(':');
        if (last < 0) {
            return false;
        }
        String body = s;
        if (s.indexOf('.') >= 0) {
            // An IPv4 address can stand for the last two groups.
            if (ipv4(s.substring(last + 1)) == null) {
                return false;
            }
            body = s.substring(0, last + 1) + "0:0";
        }
        int doubleColon = body.indexOf("::");
        if (doubleColon >= 0 && body.indexOf("::", doubleColon + 1) >= 0) {
            return false;
        }
        int groups = 0;
        for (String group : body.split(":", -1)) {
            if (group.isEmpty()) {
                continue;
            }
            if (!group.matches("[0-9a-f]{1,4}")) {
                return false;
            }
            groups++;
        }
        return doubleColon >= 0 ? groups <= 7 : groups == 8;
    }
}
