package cash.bchd.android_neutrino.wallet;

import java.util.List;

public class WalletEventListener {
    // Replies can overtake each other, and an older one would drop what a newer one added. It's
    // the listener's, as what one screen is told says nothing of what another has.
    final LatestReplies transactionReplies = new LatestReplies();
    // The balances too, which come from as many threads.
    final LatestReplies balanceReplies = new LatestReplies();

    public void onWalletReady(){}

    public void onBalanceChange(long satoshis){}

    /**
     * Called with what the wallet holds of each token category, in the order the Tokens page lists
     * them, after each balance it tells.
     */
    public void onTokenBalances(List<TokenBalance> tokens) {}

    /**
     * Called with the recovery phrase a new wallet is about to be created from, before it is, on
     * the thread that creates it, which waits. The phrase is to be kept by then, as the wallet
     * can't give it back, and the app could end before onWalletCreated.
     */
    public void onCreatingWallet(String seed) {}

    /**
     * Called once a new wallet was created, from the phrase onCreatingWallet was told.
     */
    public void onWalletCreated() {}

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