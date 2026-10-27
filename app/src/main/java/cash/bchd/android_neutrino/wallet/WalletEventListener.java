package cash.bchd.android_neutrino.wallet;

import java.util.List;

public class WalletEventListener {
    public void onWalletReady(){}

    public void onBalanceChange(long satoshis){}

    public void onWalletCreated(String seed){}

    /**
     * Called with all of the wallet's transactions, when it loads and whenever they change.
     */
    public void onGetTransactions(List<TransactionData> txs, int blockHeight) {}

    public void onBlock(int blockHeight, String blockHash) {}

    public void onScanProgress(int height) {}

    /**
     * Called when the wallet could not be loaded, with a description of the error.
     */
    public void onWalletError(String message) {}
}