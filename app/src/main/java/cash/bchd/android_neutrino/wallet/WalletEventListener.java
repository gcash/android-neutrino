package cash.bchd.android_neutrino.wallet;

import java.util.List;

public class WalletEventListener {
    public void onWalletReady(){}

    public void onBalanceChange(long satoshis){}

    public void onWalletCreated(String seed){}

    public void onGetTransactions(List<TransactionData> txs, int blockHeight) {}

    public void onBlock(int blockHeight, String blockHash) {}

    public void onTransaction(TransactionData tx) {}
}