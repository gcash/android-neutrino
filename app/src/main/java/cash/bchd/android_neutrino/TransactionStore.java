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

public class TransactionStore {

    static final String FILE_NAME = "transactionData.dat";

    /**
     * LOCK is held to save, and by whoever reads the file to change it. Reading it alone needs no
     * lock, as a save replaces the file whole, which also leaves it whole if the app is killed.
     */
    static final Object LOCK = new Object();

    // Counts the changes to the file, for a store to tell it's older.
    private static final AtomicLong SAVES = new AtomicLong();

    private final String fileName;
    private long readAt;
    private ArrayList<TransactionData> transactionDataSet = new ArrayList<TransactionData>();

    public TransactionStore(Context context, Network network) {
        fileName = fileName(network);
        readAt = SAVES.get();
        try {
            FileInputStream fis = context.openFileInput(fileName);
            ObjectInputStream is = new ObjectInputStream(fis);
            transactionDataSet = read(is);
            is.close();
            fis.close();
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

    public void save(Context context) throws Exception {
        synchronized (LOCK) {
            File file = context.getFileStreamPath(fileName);
            File tmp = new File(file.getPath() + ".tmp");
            boolean saved = false;
            try (FileOutputStream fos = new FileOutputStream(tmp);
                 ObjectOutputStream os = new ObjectOutputStream(fos)) {
                os.writeObject(transactionDataSet);
                os.flush();
                fos.getFD().sync();
                saved = tmp.renameTo(file);
                if (!saved) {
                    throw new IOException("Unable to save the transactions");
                }
                readAt = SAVES.incrementAndGet();
            } finally {
                if (!saved) {
                    tmp.delete();
                }
            }
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
     * delete removes what's saved of a network's transactions.
     */
    static void delete(Context context, Network network) {
        synchronized (LOCK) {
            context.deleteFile(fileName(network));
            context.deleteFile(fileName(network) + ".tmp");
            SAVES.incrementAndGet();
        }
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
