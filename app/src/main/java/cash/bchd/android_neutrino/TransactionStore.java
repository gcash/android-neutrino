package cash.bchd.android_neutrino;

import android.content.Context;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;

import cash.bchd.android_neutrino.wallet.TransactionData;

public class TransactionStore {
    private final String FILE_NAME = "transactionData.dat";
    HashMap<String, TransactionData> txs = new HashMap<String, TransactionData>();

    public TransactionStore(Context context) {
        try {
            FileInputStream fis = context.openFileInput(FILE_NAME);
            ObjectInputStream is = new ObjectInputStream(fis);
            txs = (HashMap) is.readObject();
            is.close();
            fis.close();
        } catch (FileNotFoundException e) {
            // Ignore file not found
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void save(Context context) throws Exception{
        FileOutputStream fos = context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE);
        ObjectOutputStream os = new ObjectOutputStream(fos);
        os.writeObject(this);
        os.close();
        fos.close();
    }

    public TransactionData getTransaction(String txid) {
        Object obj =  this.txs.get(txid);
        if (obj == null) {
            return null;
        }
        TransactionData tx = (TransactionData) obj;
        return tx;
    }

    public boolean has(String txid) {
        return (getTransaction(txid) == null);
    }

    public void putTransaction(TransactionData tx) {
        this.txs.put(tx.getTxid(), tx);
    }
}
