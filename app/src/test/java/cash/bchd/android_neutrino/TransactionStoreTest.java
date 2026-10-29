package cash.bchd.android_neutrino;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cash.bchd.android_neutrino.wallet.TransactionData;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class TransactionStoreTest {

    // saved returns a stream of the object as save writes it.
    private static ObjectInputStream saved(Object object) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream os = new ObjectOutputStream(bytes)) {
            os.writeObject(object);
        }
        return new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }

    @Test
    public void readsTheSavedTransactions() throws Exception {
        ArrayList<TransactionData> transactions = new ArrayList<>();
        transactions.add(new TransactionData("sent", false, "lunch", -5000, "$1.39", "USD", 1761500000L, "", 100));
        transactions.add(new TransactionData("received", true, "", 100000, "$27.80", "USD", 1761500100L, "", 101));

        List<TransactionData> read = TransactionStore.read(saved(transactions));
        assertEquals(2, read.size());
        assertEquals("sent", read.get(0).getTxid());
        assertEquals("lunch", read.get(0).getMemo());
        assertEquals("received", read.get(1).getTxid());
        assertEquals(100000, read.get(1).getAmount());
    }

    @Test
    public void refusesAnythingButAListOfTransactions() {
        assertThrows(InvalidObjectException.class, () -> TransactionStore.read(saved("sent")));
        assertThrows(InvalidObjectException.class,
                () -> TransactionStore.read(saved(new ArrayList<>(Collections.singletonList("sent")))));
    }
}
