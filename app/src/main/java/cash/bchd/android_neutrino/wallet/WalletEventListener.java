package cash.bchd.android_neutrino.wallet;

public class WalletEventListener {
    public void onWalletReady(){}

    public void onBalanceChange(long satoshis){}

    public void onWalletCreated(String seed){}
}