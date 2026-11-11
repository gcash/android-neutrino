package cash.bchd.android_neutrino.wallet;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import walletrpc.Api;

/**
 * AddressPayments tells the listeners of addresses of the payments to them, each output once: an
 * unmined transaction comes again in the block that mines it, and a request that counts what came
 * counted it twice. It's safe for use by several threads at once.
 */
final class AddressPayments {

    private final Map<String, AddressListener> listeners = new ConcurrentHashMap<>();
    // The outputs told of, by transaction ID and index.
    private final Set<String> told = ConcurrentHashMap.newKeySet();

    /**
     * listen has the listener told of the payments to the address, in place of the one before.
     */
    void listen(String address, AddressListener listener) {
        listeners.put(address, listener);
    }

    /**
     * tell tells the listeners of the wallet's outputs in the transactions to their addresses
     * that they weren't told of.
     */
    void tell(List<Api.TransactionDetails> txs) {
        for (Api.TransactionDetails tx : txs) {
            for (Api.TransactionDetails.Output output : tx.getCreditsList()) {
                AddressListener listener = listeners.get(output.getAddress());
                if (listener != null && told.add(Wallet.txidToString(tx.getHash()) + ":" + output.getIndex())) {
                    listener.onPaymentReceived(output.getAmount());
                }
            }
        }
    }
}
