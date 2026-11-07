package cash.bchd.android_neutrino.wallet;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    // How the transaction changed the wallet's tokens, by token ID, or null in a transaction
    // saved before the app read them.
    private ArrayList<TokenChange> tokens;
    // How much it changed the spendable balance by: amount, less the BCH of outputs with tokens.
    private long spendableAmount;

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

    /**
     * getFiatAmount returns the amount with its currency's symbol, and its sign before that, also
     * for an amount saved with the sign after the symbol.
     */
    public String getFiatAmount() {
        return this.fiatAmount == null ? null : this.fiatAmount.replaceFirst("^([^-0-9]+)-", "-$1");
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

    /**
     * getTokens returns how the transaction changed the wallet's tokens, by token ID, which is
     * nothing for a transaction saved before the app read them.
     */
    public List<TokenChange> getTokens() {
        return this.tokens != null ? Collections.unmodifiableList(this.tokens) : Collections.emptyList();
    }

    public void setTokens(List<TokenChange> tokens) {
        this.tokens = new ArrayList<>(tokens);
    }

    /**
     * getSpendableAmount returns how much the transaction changed the spendable balance by, in
     * satoshis, which leaves out the BCH that outputs with tokens carry, and is 0 for a
     * transaction saved before the app read tokens.
     */
    public long getSpendableAmount() {
        return this.spendableAmount;
    }

    /**
     * shownIncoming returns whether the transaction shows as received, as its row has it: by the
     * first token it moved, if it moved any.
     */
    public boolean shownIncoming() {
        return getTokens().isEmpty() ? this.incoming : getTokens().get(0).received();
    }

    /**
     * swapped returns whether the first token the transaction moved, which its row goes by, moved
     * as many NFTs both ways and nothing else, so that it has no direction.
     */
    public boolean swapped() {
        return !getTokens().isEmpty() && getTokens().get(0).swapped();
    }

    /**
     * shownAmount returns how much the transaction changed the balance by, as its details show it:
     * what's spendable, as in its row, if it moved tokens.
     */
    public long shownAmount() {
        return getTokens().isEmpty() ? this.amount : this.spendableAmount;
    }

    /**
     * showsBch returns whether the details show a BCH amount, which a transaction that moved tokens
     * and left the spendable balance as it was doesn't, as its tokens say what it did.
     */
    public boolean showsBch() {
        return getTokens().isEmpty() || this.spendableAmount != 0;
    }

    public void setSpendableAmount(long spendableAmount) {
        this.spendableAmount = spendableAmount;
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
