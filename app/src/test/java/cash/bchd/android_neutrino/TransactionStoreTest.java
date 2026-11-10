package cash.bchd.android_neutrino;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cash.bchd.android_neutrino.wallet.Network;
import cash.bchd.android_neutrino.wallet.TransactionData;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

public class TransactionStoreTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

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

    @Test
    public void anEmptyStoreIsStaleAndNewerThanNone() {
        TransactionStore empty = TransactionStore.empty(Network.MAINNET);
        assertTrue(empty.stale());
        assertTrue(empty.getData().isEmpty());
        assertFalse(empty.newerThan(TransactionStore.empty(Network.MAINNET)));
    }

    // storeOf returns a store of one transaction, which it hasn't saved.
    private static TransactionStore storeOf(String txid) {
        TransactionStore store = TransactionStore.empty(Network.MAINNET);
        store.setData(Collections.singletonList(
                new TransactionData(txid, true, "", 100000, "", "USD", 1761500100L, "", 101)));
        return store;
    }

    @Test
    public void savesWithNoDeleteMeanwhile() throws Exception {
        File file = new File(folder.getRoot(), TransactionStore.FILE_NAME);
        TransactionStore store = storeOf("received");
        assertTrue(store.save(file, TransactionStore.deletes()));
        assertFalse(store.stale());
        try (ObjectInputStream is = new ObjectInputStream(new FileInputStream(file))) {
            assertEquals("received", TransactionStore.read(is).get(0).getTxid());
        }
        assertFalse(new File(file.getPath() + ".tmp").exists());
    }

    @Test
    public void aDeleteWhileSavingThrowsTheSaveAway() throws Exception {
        File file = new File(folder.getRoot(), TransactionStore.FILE_NAME);
        assertTrue(storeOf("old wallet's").save(file, TransactionStore.deletes()));

        // A merge counts the deletes as it begins, and a restore deletes the file meanwhile.
        long deletes = TransactionStore.deletes();
        TransactionStore.delete(file);
        assertFalse(file.exists());
        assertFalse(storeOf("old wallet's").save(file, deletes));
        assertFalse(file.exists());
        assertFalse(new File(file.getPath() + ".tmp").exists());

        // A save that began after it saves.
        assertTrue(storeOf("new wallet's").save(file, TransactionStore.deletes()));
        assertTrue(file.exists());
    }
}
