package cash.bchd.android_neutrino.wallet;

import java.io.Serializable;

public class TransactionData implements Serializable, Comparable<TransactionData> {
    private String txid;
    private boolean incoming;
    private String memo;
    private long amount;
    private String fiatAmount;
    private String fiatCurrency;
    private long timestamp;
    private String toAddress;
    private int height;

    public TransactionData(String txid, boolean incoming, String memo, long amount, String fiatAmount,
                           String fiatCurrency, long timestamp, String toAddress, int height) {

        this.txid = txid;
        this.incoming = incoming;
        this.memo = memo;
        this.amount = amount;
        this.fiatAmount = fiatAmount;
        this.fiatCurrency = fiatCurrency;
        this.timestamp = timestamp;
        this.toAddress = toAddress;
        this.height = height;
    }

    public String getTxid() {
        return this.txid;
    }

    public boolean getIncoming() {
        return this.incoming;
    }

    public String getMemo() {
        return this.memo;
    }

    public long getAmount() {
        return this.amount;
    }

    public String getFiatAmount() {
        return this.fiatAmount;
    }

    public String getFiatCurrency() {
        return this.fiatCurrency;
    }

    public long getTimestamp() {
        return this.timestamp;
    }

    public String getToAddress() {
        return this.toAddress;
    }

    public int getHeight() {
        return this.height;
    }

    public void setFiatCurrency(String fiatCurrency) {
        this.fiatCurrency = fiatCurrency;
    }

    public void setFiatAmount(String fiatAmount) {
        this.fiatAmount = fiatAmount;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    @Override
    public int compareTo(TransactionData td) {
        if (this.timestamp > td.timestamp) {
            return 1;
        } else if (this.timestamp < td.timestamp) {
            return -1;
        }
        return 0;
    }
}
