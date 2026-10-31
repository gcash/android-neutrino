package cash.bchd.android_neutrino.wallet;

import walletrpc.Api;

/**
 * TokensHeld is what the wallet holds with CashTokens, which the app doesn't show or send yet:
 * the number of token categories, and the BCH that the outputs with tokens carry, which the
 * spendable balance leaves out.
 */
public final class TokensHeld {

    public final int categories;
    public final long satoshis;

    TokensHeld(int categories, long satoshis) {
        this.categories = categories;
        this.satoshis = satoshis;
    }

    /**
     * of reads what the wallet holds with tokens from its token balances and its balance, both
     * with no confirmations required. The balance's total counts the outputs with tokens, which
     * its spendable balance and immature rewards leave out.
     */
    static TokensHeld of(Api.TokenBalancesResponse tokens, Api.BalanceResponse balance) {
        return new TokensHeld(tokens.getBalancesCount(),
                balance.getTotal() - balance.getSpendable() - balance.getImmatureReward());
    }

    /**
     * any returns whether the wallet holds tokens.
     */
    public boolean any() {
        return categories > 0;
    }
}
