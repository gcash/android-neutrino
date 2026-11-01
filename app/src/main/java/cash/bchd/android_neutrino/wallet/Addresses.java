package cash.bchd.android_neutrino.wallet;

/**
 * Addresses are the two forms of one of the wallet's addresses: the plain one, and the
 * token-aware one, which wallets send CashTokens to as well as BCH.
 */
public final class Addresses {

    public final String plain;
    public final String tokenAware;

    Addresses(String plain, String tokenAware) {
        this.plain = plain;
        this.tokenAware = tokenAware;
    }

    interface Current {
        // address returns the wallet's current address, in its token-aware form if tokenAware.
        String address(boolean tokenAware) throws Exception;
    }

    /**
     * current returns both forms of the wallet's current address, which it asks for one at a
     * time. The wallet moves on to its next address once a payment to the current one comes in,
     * so it asks again until the plain form after the token-aware one is the one before it.
     */
    static Addresses current(Current wallet) throws Exception {
        String plain = wallet.address(false);
        while (true) {
            String tokenAware = wallet.address(true);
            String after = wallet.address(false);
            if (after.equals(plain)) {
                return new Addresses(plain, tokenAware);
            }
            plain = after;
        }
    }
}
