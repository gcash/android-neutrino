package cash.bchd.android_neutrino;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import cash.bchd.android_neutrino.wallet.Network;
import cash.bchd.android_neutrino.wallet.TransactionData;
import cash.bchd.android_neutrino.wallet.TransactionList;

public class TransactionStore {

    static final String FILE_NAME = "transactionData.dat";

    /**
     * LOCK is held to save, and by whoever reads the file to change it. Reading it alone needs no
     * lock, as a save replaces the file whole, which also leaves it whole if the app is killed.
     * Deleting needs none either, as a restore does on the main thread, and a save of a large list
     * can hold the lock for seconds: a save that sees a delete since it began throws its file away.
     */
    static final Object LOCK = new Object();

    // Counts the deletes, for a save to tell one came while it saved.
    private static final AtomicLong DELETES = new AtomicLong();

    // Counts the changes to the file, for a store to tell it's older.
    private static final AtomicLong SAVES = new AtomicLong();

    private final String fileName;
    private long readAt;
    private ArrayList<TransactionData> transactionDataSet = new ArrayList<TransactionData>();

    private TransactionStore(Network network) {
        fileName = fileName(network);
        readAt = -1;
    }

    /**
     * empty returns a store that has read nothing yet, and is stale, for a screen to show while
     * the file is read.
     */
    static TransactionStore empty(Network network) {
        return new TransactionStore(network);
    }

    /**
     * newerThan returns whether this store has what's in the file as it was later than the other
     * one does, as two read at once can come in out of order.
     */
    boolean newerThan(TransactionStore other) {
        return readAt > other.readAt;
    }

    public TransactionStore(Context context, Network network) {
        fileName = fileName(network);
        readAt = SAVES.get();
        // Both closed whether the file reads or not, as an unreadable one, read on each merge, left
        // a file open each time.
        try (FileInputStream fis = context.openFileInput(fileName);
             ObjectInputStream is = new ObjectInputStream(fis)) {
            transactionDataSet = read(is);
        } catch (FileNotFoundException | NotSerializableException e) {
            // Ignore file not found
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * read returns the transactions that save wrote to the stream. It refuses anything but a list
     * of TransactionData, which leaves the store empty, as an unreadable file does.
     */
    static ArrayList<TransactionData> read(ObjectInputStream is) throws IOException, ClassNotFoundException {
        Object saved = is.readObject();
        if (!(saved instanceof List)) {
            throw new InvalidObjectException("not a list of transactions");
        }
        ArrayList<TransactionData> transactions = new ArrayList<>();
        for (Object tx : (List<?>) saved) {
            if (!(tx instanceof TransactionData)) {
                throw new InvalidObjectException("not a transaction");
            }
            transactions.add((TransactionData) tx);
        }
        return transactions;
    }

    /**
     * deletes returns how many deletes there were, for a save to tell one came while it saved.
     */
    static long deletes() {
        return DELETES.get();
    }

    /**
     * save saves the transactions to the file, unless a delete came since deletes counted them,
     * and returns whether it did. A delete that comes as the file is renamed has it removed after.
     */
    boolean save(File file, long deletes) throws IOException {
        synchronized (LOCK) {
            File tmp = new File(file.getPath() + ".tmp");
            boolean saved = false;
            try (FileOutputStream fos = new FileOutputStream(tmp);
                 ObjectOutputStream os = new ObjectOutputStream(fos)) {
                os.writeObject(transactionDataSet);
                os.flush();
                fos.getFD().sync();
                if (DELETES.get() != deletes) {
                    return false;
                }
                saved = tmp.renameTo(file);
                if (!saved) {
                    throw new IOException("Unable to save the transactions");
                }
                if (DELETES.get() != deletes) {
                    file.delete();
                    return false;
                }
                readAt = SAVES.incrementAndGet();
                return true;
            } finally {
                if (!saved) {
                    tmp.delete();
                }
            }
        }
    }

    /**
     * merge saves what the wallet told, over what's in the file, and returns the store. It reads
     * and writes the whole file, so it's not for the UI thread, and it takes the file as it is, as
     * another screen can have saved what only it was told, such as a memo.
     */
    static TransactionStore merge(Context context, Network network, List<TransactionData> wallet) {
        synchronized (LOCK) {
            long deletes = DELETES.get();
            TransactionStore store = new TransactionStore(context, network);
            store.setData(TransactionList.rebuild(store.getData(), wallet));
            try {
                store.save(context.getFileStreamPath(store.fileName), deletes);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return store;
        }
    }

    /**
     * stale returns whether the file changed since this store read or saved it, as another screen
     * saves it while it listens.
     */
    public boolean stale() {
        return readAt != SAVES.get();
    }

    /**
     * delete removes what's saved of a network's transactions. It doesn't wait for a save, which
     * throws its file away instead.
     */
    static void delete(Context context, Network network) {
        delete(context.getFileStreamPath(fileName(network)));
    }

    /**
     * delete removes the file of a store, and one being saved.
     */
    static void delete(File file) {
        DELETES.incrementAndGet();
        file.delete();
        new File(file.getPath() + ".tmp").delete();
        SAVES.incrementAndGet();
    }

    /**
     * fileName returns the name of the file of the network's transactions. Mainnet's keeps the
     * name earlier versions gave it.
     */
    static String fileName(Network network) {
        return network == Network.MAINNET ? FILE_NAME : "transactionData_" + network.id + ".dat";
    }

    public List<TransactionData> getData() {
        return this.transactionDataSet;
    }

    public void setData(List<TransactionData> data) {
        this.transactionDataSet = new ArrayList<TransactionData>(data);
    }
}
