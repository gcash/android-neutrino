package cash.bchd.android_neutrino.wallet;

import java.io.Serializable;

public class TransactionData implements Serializable, Comparable<TransactionData> {
    // The version Java computed for the class before it had moreRecipients and toYourself, so
    // that the app still reads the transactions it saved then.
    private static final long serialVersionUID = 9222781885561699409L;

    private String txid;
    private boolean incoming;
    private String memo;
    private long amount;
    private String fiatAmount;
    private String fiatCurrency;
    private long timestamp;
    private String toAddress;
    private int height;
    // How many more addresses than toAddress a sent transaction paid.
    private int moreRecipients;
    // Whether every output of a sent transaction pays the wallet.
    private boolean toYourself;

    /**
     * SentTo is whom the transaction list says a sent transaction paid.
     */
    public enum SentTo {
        ADDRESS,
        ADDRESS_AND_MORE,
        YOURSELF,
        // No output that pays someone else pays an address, such as OP_RETURN data.
        NO_ADDRESS
    }

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

    public int getMoreRecipients() {
        return this.moreRecipients;
    }

    public boolean getToYourself() {
        return this.toYourself;
    }

    public SentTo getSentTo() {
        if (this.toYourself) {
            return SentTo.YOURSELF;
        }
        if (this.toAddress == null || this.toAddress.isEmpty()) {
            return SentTo.NO_ADDRESS;
        }
        return this.moreRecipients > 0 ? SentTo.ADDRESS_AND_MORE : SentTo.ADDRESS;
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

    /**
     * setRecipients sets whom a sent transaction paid: an address, how many more addresses, and
     * whether every output pays the wallet.
     */
    public void setRecipients(String toAddress, int moreRecipients, boolean toYourself) {
        this.toAddress = toAddress;
        this.moreRecipients = moreRecipients;
        this.toYourself = toYourself;
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
