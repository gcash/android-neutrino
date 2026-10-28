package cash.bchd.android_neutrino.wallet;

import java.util.Locale;

/**
 * Network is a Bitcoin Cash network the app runs a wallet on. Each network has a wallet of its
 * own, with its own recovery phrase, settings and transactions, so that test coins and real ones
 * never mix.
 */
public enum Network {
    MAINNET("mainnet", "bitcoincash", "", 1231006505L, 1501545600L, true),
    // Testnet4's coins have no value, and its first block was mined on August 19, 2020.
    TESTNET4("testnet4", "bchtest", "testnet4=1", 1597811185L, 1597811185L, false);

    // The name of the network's directory in bchwallet's data, which also saves the choice.
    public final String id;
    // The scheme of the network's payment URIs, which is also its addresses' prefix.
    public final String scheme;
    // The line of bchwallet's config that selects the network, or "" for mainnet, its default.
    final String configOption;
    // The time of the network's first block, in seconds, before which no wallet has coins.
    public final long firstBlockTime;
    // The birthday that a restored wallet scans from unless the user picks another: Bitcoin
    // Cash's split from Bitcoin on August 1, 2017 on mainnet, and the first block on testnet4.
    public final long defaultRestoreBirthday;
    // Whether the network's coins have a price, so that amounts are shown in fiat too.
    public final boolean hasFiat;

    Network(String id, String scheme, String configOption, long firstBlockTime,
            long defaultRestoreBirthday, boolean hasFiat) {
        this.id = id;
        this.scheme = scheme;
        this.configOption = configOption;
        this.firstBlockTime = firstBlockTime;
        this.defaultRestoreBirthday = defaultRestoreBirthday;
        this.hasFiat = hasFiat;
    }

    /**
     * uriPrefix returns what the network's payment URIs start with, such as "bitcoincash:".
     */
    public String uriPrefix() {
        return scheme + ":";
    }

    /**
     * fromId returns the network with the id, or mainnet for an unknown one.
     */
    public static Network fromId(String id) {
        for (Network network : values()) {
            if (network.id.equals(id)) {
                return network;
            }
        }
        return MAINNET;
    }

    /**
     * ofUri returns the network whose payment URIs the text starts like, ignoring case, as QR
     * codes often write URIs in capitals, or null if it starts like none.
     */
    public static Network ofUri(String text) {
        if (text == null) {
            return null;
        }
        String lower = text.trim().toLowerCase(Locale.ROOT);
        for (Network network : values()) {
            if (lower.startsWith(network.uriPrefix())) {
                return network;
            }
        }
        return null;
    }
}
