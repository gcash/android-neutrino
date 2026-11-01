package cash.bchd.android_neutrino.wallet;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class AddressesTest {

    // A wallet whose current address is each of the addresses in turn, a plain one and its
    // token-aware form, moving on to the next one after the calls given, as a payment would make
    // it.
    private static final class FakeWallet implements Addresses.Current {
        private final Iterator<Integer> moves;
        private final List<String> calls = new ArrayList<>();
        private int address;
        private int next;

        FakeWallet(Integer... moveAfter) {
            moves = Arrays.asList(moveAfter).iterator();
            next = moves.hasNext() ? moves.next() : -1;
        }

        @Override
        public String address(boolean tokenAware) {
            String form = (tokenAware ? "z" : "q") + address;
            calls.add(form);
            if (calls.size() == next) {
                address++;
                next = moves.hasNext() ? moves.next() : -1;
            }
            return form;
        }
    }

    @Test
    public void asksForBothFormsOfTheAddress() throws Exception {
        FakeWallet wallet = new FakeWallet();
        Addresses addresses = Addresses.current(wallet);
        assertEquals("q0", addresses.plain);
        assertEquals("z0", addresses.tokenAware);
        assertEquals(Arrays.asList("q0", "z0", "q0"), wallet.calls);
    }

    @Test
    public void asksAgainWhenTheAddressMovesOn() throws Exception {
        // A payment to q0 comes in after the first call, so the token-aware form is z1's.
        FakeWallet wallet = new FakeWallet(1);
        Addresses addresses = Addresses.current(wallet);
        assertEquals("q1", addresses.plain);
        assertEquals("z1", addresses.tokenAware);
        assertEquals(Arrays.asList("q0", "z1", "q1", "z1", "q1"), wallet.calls);
    }

    @Test
    public void asksAgainUntilTheAddressStays() throws Exception {
        // Payments come in after the second and the fourth calls.
        FakeWallet wallet = new FakeWallet(2, 4);
        Addresses addresses = Addresses.current(wallet);
        assertEquals("q2", addresses.plain);
        assertEquals("z2", addresses.tokenAware);
        assertEquals(Arrays.asList("q0", "z0", "q1", "z1", "q2", "z2", "q2"), wallet.calls);
    }
}
