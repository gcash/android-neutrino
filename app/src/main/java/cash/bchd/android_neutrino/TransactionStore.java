package cash.bchd.android_neutrino;

import android.content.Context;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import cash.bchd.android_neutrino.wallet.TransactionData;

public class TransactionStore {

    private final String FILE_NAME = "transactionData.dat";
    private ArrayList<TransactionData> transactionDataSet = new ArrayList<TransactionData>();

    public TransactionStore(Context context) {
        try {
            FileInputStream fis = context.openFileInput(FILE_NAME);
            ObjectInputStream is = new ObjectInputStream(fis);
            transactionDataSet = (ArrayList<TransactionData>) is.readObject();
            is.close();
            fis.close();
        } catch (FileNotFoundException | NotSerializableException e) {
            // Ignore file not found
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void save(Context context) throws Exception {
        FileOutputStream fos = context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE);
        ObjectOutputStream os = new ObjectOutputStream(fos);
        os.writeObject(transactionDataSet);
        os.close();
        fos.close();
    }

    public List<TransactionData> getData() {
        return this.transactionDataSet;
    }

    public void setData(List<TransactionData> data) {
        this.transactionDataSet = new ArrayList<TransactionData>(data);
    }
}
