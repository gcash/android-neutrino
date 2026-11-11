package cash.bchd.android_neutrino.wallet;

import com.google.protobuf.ByteString;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import walletrpc.Api;

import static org.junit.Assert.assertEquals;

public class AddressPaymentsTest {

    private static Api.TransactionDetails tx(int id, String address, long... amounts) {
        Api.TransactionDetails.Builder tx = Api.TransactionDetails.newBuilder()
                .setHash(ByteString.copyFrom(new byte[]{(byte) id}));
        for (int i = 0; i < amounts.length; i++) {
            tx.addCredits(Api.TransactionDetails.Output.newBuilder()
                    .setIndex(i).setAddress(address).setAmount(amounts[i]));
        }
        return tx.build();
    }

    @Test
    public void tellsEachPaymentOnce() {
        AddressPayments payments = new AddressPayments();
        List<Long> told = new ArrayList<>();
        payments.listen("a", new AddressListener() {
            @Override
            public void onPaymentReceived(long amount) {
                told.add(amount);
            }
        });
        Api.TransactionDetails partial = tx(1, "a", 60_000_000L);
        // Unmined, then in the block that mines it, then again in a block recovery brings.
        payments.tell(Collections.singletonList(partial));
        payments.tell(Collections.singletonList(partial));
        payments.tell(Collections.singletonList(partial));
        assertEquals(Collections.singletonList(60_000_000L), told);

        // Each output of another transaction, and none to another address.
        payments.tell(Collections.singletonList(tx(2, "a", 1000L, 2000L)));
        payments.tell(Collections.singletonList(tx(3, "b", 5000L)));
        assertEquals(List.of(60_000_000L, 1000L, 2000L), told);
    }

    @Test
    public void tellsNoListenerThatStopped() {
        AddressPayments payments = new AddressPayments();
        List<String> told = new ArrayList<>();
        AddressListener closed = new AddressListener() {
            @Override
            public void onPaymentReceived(long amount) {
                told.add("closed");
            }
        };
        AddressListener open = new AddressListener() {
            @Override
            public void onPaymentReceived(long amount) {
                told.add("open");
            }
        };
        payments.listen("a", closed);
        payments.stop("a", closed);
        payments.tell(Collections.singletonList(tx(1, "a", 1000L)));
        assertEquals(Collections.emptyList(), told);

        // One that stops after another took its place leaves that one listening.
        payments.listen("a", closed);
        payments.listen("a", open);
        payments.stop("a", closed);
        payments.tell(Collections.singletonList(tx(2, "a", 1000L)));
        assertEquals(Collections.singletonList("open"), told);
    }
}
