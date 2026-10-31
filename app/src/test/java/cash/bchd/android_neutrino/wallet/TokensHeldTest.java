package cash.bchd.android_neutrino.wallet;

import org.junit.Test;

import walletrpc.Api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TokensHeldTest {

    @Test
    public void countsTheBchThatTokensCarry() {
        // The total counts the outputs with tokens, which the spendable balance and immature
        // rewards leave out.
        Api.TokenBalancesResponse tokens = Api.TokenBalancesResponse.newBuilder()
                .addBalances(Api.TokenBalancesResponse.Balance.newBuilder().setCategory("ab").setAmount(5))
                .addBalances(Api.TokenBalancesResponse.Balance.newBuilder().setCategory("cd").setAmount(1))
                .build();
        Api.BalanceResponse balance = Api.BalanceResponse.newBuilder()
                .setTotal(100000 + 2000 + 5000).setSpendable(100000).setImmatureReward(5000).build();

        TokensHeld held = TokensHeld.of(tokens, balance);
        assertTrue(held.any());
        assertEquals(2, held.categories);
        assertEquals(2000, held.satoshis);
    }

    @Test
    public void holdsNoneWithoutTokens() {
        Api.BalanceResponse balance = Api.BalanceResponse.newBuilder().setTotal(1000).setSpendable(1000).build();

        TokensHeld held = TokensHeld.of(Api.TokenBalancesResponse.getDefaultInstance(), balance);
        assertFalse(held.any());
        assertEquals(0, held.satoshis);
    }
}
