package cash.bchd.android_neutrino;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import cash.bchd.android_neutrino.tokens.TokenIcons;
import cash.bchd.android_neutrino.wallet.Network;
import cash.bchd.android_neutrino.wallet.Wallet;
import cash.bchd.android_neutrino.wallet.WalletEventListener;
import walletrpc.Api;

public class SettingsFragment extends PreferenceFragmentCompat {

    public static final String FRAGMENT_TAG = "my_preference_fragment";

    public static final String DONATE_URI = "bitcoincash:qrhea03074073ff3zv9whh0nggxc7k03ssh8jv9mkx?label=The%20bchd%20project&message=<3%20BCHD";

    private static Settings settings;

    public static String activeScreen;

    public SettingsFragment() {

    }

    // What tells this screen of blocks and rescans, which ends with it, as the wallet outlives it.
    private WalletEventListener blockchainListener;
    private Runnable stopRescanListener;

    // Asks the wallet where its chain is off the main thread, as a busy wallet answers late.
    private static final ExecutorService CHAIN_ASKER = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean CHAIN_ASKING = new AtomicBoolean();

    @Override
    public void onDestroy() {
        Wallet wallet = Wallet.getInstance();
        if (wallet != null && blockchainListener != null) {
            wallet.stopListening(blockchainListener);
        }
        if (stopRescanListener != null) {
            stopRescanListener.run();
        }
        super.onDestroy();
    }

    // askChain asks the wallet where its chain is off the main thread, saves it, and shows it in
    // the preference once it answers, if this screen is still there. While one ask waits on a
    // busy wallet, another isn't queued behind it, and the screen isn't held meanwhile.
    private void askChain() {
        Wallet wallet = Wallet.getInstance();
        if (wallet == null || !CHAIN_ASKING.compareAndSet(false, true)) {
            return;
        }
        Settings saved = settings;
        WeakReference<SettingsFragment> ref = new WeakReference<>(this);
        CHAIN_ASKER.execute(() -> {
            Api.NetworkResponse net;
            try {
                net = wallet.network();
            } catch (Exception e) {
                e.printStackTrace();
                return;
            } finally {
                CHAIN_ASKING.set(false);
            }
            saved.setLastBlockHeight(net.getBestHeight());
            saved.setLastBlockHash(net.getBestBlock());
            saved.setScanHeight(net.getSyncedTo());
            SettingsFragment fragment = ref.get();
            Activity activity = fragment == null ? null : fragment.getActivity();
            if (activity == null) {
                return;
            }
            activity.runOnUiThread(() -> {
                SettingsFragment shown = ref.get();
                Preference blockchainPref = shown == null || !shown.isAdded() ? null : shown.findPreference("blockchain");
                if (blockchainPref == null) {
                    return;
                }
                String blockchainInfo = "Height: " + net.getBestHeight() + "\nHash: " + net.getBestBlock() + "\nScanned Through: " + net.getSyncedTo();
                blockchainPref.setSummary(blockchainInfo);
            });
        });
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        // Android can bring back any of these screens in a new process.
        settings = Settings.get(requireContext());
        // The wallet that runs now, as the one that ran when the screen opened may have stopped
        // since, and started again.
        Wallet wallet = Wallet.getInstance();

        if (rootKey == null) {
            activeScreen = "root";
            if (settings==null) {
                return;
            }

            // What was saved of the chain at once, and what the wallet says when it answers.
            Preference blockchainPref = (Preference) findPreference("blockchain");
            String blockchainInfo = "Height: " + settings.getLastBlockHeight() + "\nHash: " + settings.getLastBlockHash() + "\nScanned Through: " + settings.getScanHeight();
            blockchainPref.setSummary(blockchainInfo);
            askChain();
            blockchainPref.setOnPreferenceClickListener(preference -> {
                askChain();
                return false;
            });

            Preference backupPref = (Preference) findPreference("backup");
            PreferenceScreen prefScreen = (PreferenceScreen) findPreference("preferenceScreen");
            if (settings.getMnemonic().equals("")) {
                prefScreen.removePreference(backupPref);
            }

            ListPreference networkPref = findPreference("network");
            Network network = settings.getNetwork();
            networkPref.setValue(network.id);
            networkPref.setSummary(getString(R.string.network_summary, Networks.name(requireContext(), network)));
            networkPref.setOnPreferenceChangeListener((preference, o) -> {
                Network chosen = Network.fromId(o.toString());
                if (chosen != network) {
                    confirmNetworkSwitch(chosen);
                }
                return false;
            });

            Preference currencyPref = (Preference) findPreference("currency_preference");
            currencyPref.setSummary(settings.getFiatCurrency().toUpperCase(Locale.ROOT));
            currencyPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    settings.setFiatCurrency(o.toString());
                    currencyPref.setSummary(o.toString().toUpperCase(Locale.ROOT));
                    return false;
                }
            });

            Preference labelPref = (Preference) findPreference("label");
            String defaultLabel = settings.getDefaultLabel();
            String defaultLabelString = "Pre-populate the label field in the payment request with the value entered here. Typically this field is your name or your business' name.";
            if (defaultLabel.equals("")) {
                labelPref.setSummary(defaultLabelString);
            } else {
                labelPref.setSummary(defaultLabel);
            }
            labelPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    if (o.toString().equals("")) {
                        labelPref.setSummary(defaultLabelString);
                    } else {
                        labelPref.setSummary(o.toString());
                    }
                    settings.setDefaultLabel(o.toString());
                    return false;
                }
            });

            Preference memoPref = (Preference) findPreference("memo");
            String defaultMemo = settings.getDefaultMemo();
            String defaultMemoString = "Pre-populate the memo field in the payment request with the value entered here. Typically this field describes what the payment is for.";
            if (defaultMemo.equals("")) {
                memoPref.setSummary(defaultMemoString);
            } else {
                memoPref.setSummary(defaultMemo);
            }
            memoPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    if (o.toString().equals("")) {
                        memoPref.setSummary(defaultMemoString);
                    } else {
                        memoPref.setSummary(o.toString());
                    }
                    settings.setDefaultMemo(o.toString());
                    return false;
                }
            });

            SwitchPreferenceCompat blocksOnlyPref = (SwitchPreferenceCompat) findPreference("blocksonly");
            blocksOnlyPref.setChecked(settings.getBlocksOnly());
            blocksOnlyPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    boolean blockOnly = (Boolean) o;
                    settings.setBlocksOnly(blockOnly);
                    return true;
                }
            });

            EditTextPreference peersPref = (EditTextPreference) findPreference("peers");
            String peers = settings.getPeers();
            peersPref.setText(peers);
            if (!peers.equals("")) {
                peersPref.setSummary(peers);
            }
            peersPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    settings.setPeers(o.toString().trim());
                    String joined = String.join(", ", settings.getPeerList());
                    settings.setPeers(joined);
                    peersPref.setText(joined);
                    peersPref.setSummary(joined.equals("") ? getString(R.string.peers_summary) : joined);
                    return false;
                }
            });

            SwitchPreferenceCompat peersOnlyPref = (SwitchPreferenceCompat) findPreference("peersOnly");
            peersOnlyPref.setChecked(settings.getPeersOnly());
            peersOnlyPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    settings.setPeersOnly((Boolean) o);
                    return true;
                }
            });

            EditTextPreference feePref = (EditTextPreference) findPreference("fee");
            feePref.setSummary(settings.getFeePerByte() + " sat/byte");
            feePref.setText(String.valueOf(settings.getFeePerByte()));
            feePref.setOnBindEditTextListener(editText -> editText.setInputType(InputType.TYPE_CLASS_NUMBER));
            feePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    int fee;
                    try {
                        fee = Integer.parseInt(o.toString().trim());
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    if (fee < 1) {
                        return false;
                    }
                    settings.setFeePerByte(fee);
                    feePref.setSummary(fee + " sat/byte");
                    feePref.setText(String.valueOf(fee));
                    return false;
                }
            });

            Preference lovePref = (Preference) findPreference("love");
            // The donation address is a mainnet one.
            if (network != Network.MAINNET) {
                prefScreen.removePreference(lovePref);
            }
            lovePref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {

                    Intent intent = new Intent(getContext(), SendActivity.class);
                    intent.putExtra("fiatCurrency", settings.getFiatCurrency());
                    intent.putExtra("feePerByte", settings.getFeePerByte());
                    intent.putExtra("qrdata", DONATE_URI);
                    startActivity(intent);
                    return false;
                }
            });
            WalletEventListener listener = new WalletEventListener() {
                @Override
                public void onBlock(int blockHeight, String blockHash) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(new Runnable() {

                            @Override
                            public void run() {
                                String blockchainInfo = "Height: " + blockHeight + "\nHash: " + blockHash + "\nScanned Through: " + settings.getScanHeight();
                                blockchainPref.setSummary(blockchainInfo);
                            }
                        });
                    }
                }

                @Override
                public void onScanProgress(int height) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(new Runnable() {

                            @Override
                            public void run() {
                                String blockchainInfo = "Height: " + settings.getLastBlockHeight() + "\nHash: " + settings.getLastBlockHash() + "\nScanned Through: " + height;
                                blockchainPref.setSummary(blockchainInfo);
                            }
                        });
                    }
                }
            };

            if (wallet != null) {
                stopRescanListener = wallet.listenRescan(listener);
                wallet.listenBlockchain(listener);
                blockchainListener = listener;
            }
        } else if (rootKey.equals("tokenSettings")) {
            activeScreen = "tokenSettings";
            androidx.preference.SwitchPreferenceCompat imagesPref = findPreference("tokenImages");
            if (imagesPref != null) {
                imagesPref.setChecked(settings.getTokenImages());
                imagesPref.setOnPreferenceChangeListener((preference, o) -> {
                    settings.setTokenImages((Boolean) o);
                    TokenIcons.configure((Boolean) o, settings.getLoadedIcons());
                    return true;
                });
            }
            androidx.preference.SwitchPreferenceCompat autoLoadPref = findPreference("tokenAutoLoad");
            if (autoLoadPref != null) {
                autoLoadPref.setChecked(settings.getTokenAutoLoad());
                autoLoadPref.setOnPreferenceChangeListener((preference, o) -> {
                    settings.setTokenAutoLoad((Boolean) o);
                    return true;
                });
            }
            ListPreference storagePref = findPreference("nftStorage");
            if (storagePref != null) {
                showNftStorage(storagePref, settings.getNftStorageMb());
                storagePref.setOnPreferenceChangeListener((preference, o) -> {
                    int mb = Integer.parseInt(o.toString());
                    settings.setNftStorageMb(mb);
                    showNftStorage(storagePref, mb);
                    return false;
                });
            }
            EditTextPreference gatewaysPref = findPreference("ipfsGateways");
            if (gatewaysPref != null) {
                gatewaysPref.setText(settings.getIpfsGatewaysText());
                gatewaysPref.setOnPreferenceChangeListener((preference, o) -> {
                    String gateways = o.toString();
                    // Nothing at all asks for the gateways the app comes with.
                    if (gateways.trim().isEmpty()) {
                        settings.resetIpfsGateways();
                        gatewaysPref.setText(settings.getIpfsGatewaysText());
                        return false;
                    }
                    // Tokens on IPFS would fail with no word why, so the list stays as it was.
                    if (Settings.parseGateways(gateways).length == 0) {
                        Toast.makeText(requireContext(), R.string.ipfs_gateways_invalid, Toast.LENGTH_LONG).show();
                        return false;
                    }
                    settings.setIpfsGateways(gateways);
                    return true;
                });
            }
        } else if (rootKey.equals("bchd")) {
            activeScreen = "bchd";
            Preference ipPref = (Preference) findPreference("ip");
            String ip = settings.getBchdIP();
            String defaultIPString = "Enter the IP address (IP:Port) of the bchd full node";
            if (ip.equals("")) {
                ipPref.setSummary(defaultIPString);
            } else {
                ipPref.setSummary(ip);
            }
            ipPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    if (o.toString().equals("")) {
                        ipPref.setSummary(defaultIPString);
                    } else {
                        ipPref.setSummary(o.toString());
                    }
                    settings.setBchdIP(o.toString());
                    return false;
                }
            });

            Preference usernamePref = (Preference) findPreference("username");
            String username = settings.getBchdUsername();
            String defaultUsernameString = "Enter the bchd RPC username";
            if (username.equals("")) {
                usernamePref.setSummary(defaultUsernameString);
            } else {
                usernamePref.setSummary(username);
            }
            usernamePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    if (o.toString().equals("")) {
                        usernamePref.setSummary(defaultUsernameString);
                    } else {
                        usernamePref.setSummary(o.toString());
                    }
                    settings.setBchdUsername(o.toString());
                    return false;
                }
            });

            Preference passwordPref = (Preference) findPreference("password");
            String password = settings.getBchdPassword();
            String defaultPasswordString = "Enter the bchd RPC password";
            if (password.equals("")) {
                passwordPref.setSummary(defaultPasswordString);
            } else {
                passwordPref.setSummary(password);
            }
            passwordPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    if (o.toString().equals("")) {
                        passwordPref.setSummary(defaultPasswordString);
                    } else {
                        passwordPref.setSummary(o.toString());
                    }
                    settings.setBchdPassword(o.toString());
                    return false;
                }
            });

            Preference certPref = (Preference) findPreference("cert");
            String cert = settings.getBchdCert();
            String defaultCertString = "Paste the contents of the bchd rpc.cert file for authentication";
            if (cert.equals("")) {
                certPref.setSummary(defaultCertString);
            } else {
                certPref.setSummary(cert);
            }
            certPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    if (o.toString().equals("")) {
                        certPref.setSummary(defaultCertString);
                    } else {
                        certPref.setSummary(o.toString());
                    }
                    settings.setBchdCert(o.toString());
                    return false;
                }
            });
        }
    }

    // confirmNetworkSwitch asks whether to switch the app to the network's wallet, and switches.
    // showNftStorage shows the NFT storage chosen.
    private void showNftStorage(ListPreference pref, int mb) {
        pref.setValue(String.valueOf(mb));
        pref.setSummary(mb == 0 ? getString(R.string.nft_storage_summary_unlimited)
                : getString(R.string.nft_storage_summary, pref.getEntry()));
    }

    private void confirmNetworkSwitch(Network network) {
        String to = Networks.name(requireContext(), network);
        String from = Networks.name(requireContext(), settings.getNetwork());
        String message = network == Network.MAINNET
                ? getString(R.string.switch_to_mainnet_message, from)
                : getString(R.string.switch_to_test_network_message, to, from);
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.switch_network_title, to))
                .setMessage(message)
                .setPositiveButton(R.string.switch_network, (dialog, which) -> Networks.switchTo(requireActivity(), network))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        if (preference.getKey().equals("rescan")) {
            AlertDialog.Builder alert = new AlertDialog.Builder(getContext());
            alert.setTitle("Rescan");

            alert.setMessage("Rescan from wallet birthday?");
            alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    // Canceled.
                }
            });
            alert.setPositiveButton("OK", (dialog, whichButton) -> rescan());
            alert.setCancelable(true);
            alert.show();
        } else {
            super.onDisplayPreferenceDialog(preference);
        }
    }

    // rescan has the wallet scan the blocks from its birthday again and shows that it scans
    // them from the start. It asks the wallet that runs now, as the one the screen opened with
    // may have stopped since, off the main thread, and says why when the wallet can't rescan.
    private void rescan() {
        Wallet current = Wallet.getInstance();
        if (current == null || !current.isRunning()) {
            Toast.makeText(requireContext(), R.string.wallet_is_not_loaded_yet, Toast.LENGTH_LONG).show();
            return;
        }
        Context context = requireContext().getApplicationContext();
        Futures.addCallback(current.rescanAsync(), new FutureCallback<Api.RescanResponse>() {
            @Override
            public void onSuccess(Api.RescanResponse response) {
                settings.setScanHeight(0);
                Preference blockchainPref = isAdded() ? findPreference("blockchain") : null;
                if (blockchainPref != null) {
                    blockchainPref.setSummary("Height: " + settings.getLastBlockHeight() + "\nHash: "
                            + settings.getLastBlockHash() + "\nScanned Through: 0");
                }
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                String reason = Wallet.reasonFor(t);
                Toast.makeText(context, context.getString(R.string.rescan_failed,
                        reason != null ? reason : t.toString()), Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(context));
    }

    @Override
    public void onResume() {
        super.onResume();
        showHiddenTokens();
    }

    // showHiddenTokens shows the entry of the hidden tokens while any is hidden, with how many,
    // also after the screen it opens showed some again.
    private void showHiddenTokens() {
        Preference hiddenTokens = findPreference("hiddenTokens");
        if (hiddenTokens == null || settings == null) {
            return;
        }
        int hidden = settings.getHiddenTokens().size();
        hiddenTokens.setVisible(hidden > 0);
        hiddenTokens.setSummary(getResources().getQuantityString(R.plurals.hidden_tokens_summary, hidden, hidden));
    }
}