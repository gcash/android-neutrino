package cash.bchd.android_neutrino;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import com.takisoft.fix.support.v7.preference.EditTextPreference;

import android.support.v7.app.AlertDialog;
import android.support.v7.preference.Preference;
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.SwitchPreferenceCompat;
import android.view.View;

import cash.bchd.android_neutrino.wallet.Wallet;
import cash.bchd.android_neutrino.wallet.WalletEventListener;
import walletrpc.Api;

public class SettingsFragment extends PreferenceFragmentCompat {

    public static final String FRAGMENT_TAG = "my_preference_fragment";

    public static final String DONATE_URI = "bitcoincash:qrhea03074073ff3zv9whh0nggxc7k03ssh8jv9mkx?label=The%20bchd%20project&message=<3%20BCHD";

    private static Settings settings;
    private static Wallet wallet;

    public static String activeScreen;

    public SettingsFragment() {

    }

    @Override
    public void onCreatePreferencesFix(Bundle bundle, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        settings = Settings.getInstance();
        wallet = Wallet.getInstance();

        if (rootKey == null) {
            activeScreen = "root";
            try {
                Api.NetworkResponse net = wallet.network();
                settings.setLastBlockHeight(net.getBestHeight());
                settings.setLastBlockHash(net.getBestBlock());
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (settings==null) {
                return;
            }

            Preference blockchainPref = (Preference) findPreference("blockchain");
            String blockchainInfo = "Height: " + settings.getLastBlockHeight() + "\nHash: " + settings.getLastBlockHash();
            blockchainPref.setSummary(blockchainInfo);

            Preference backupPref = (Preference) findPreference("backup");
            PreferenceScreen prefScreen = (PreferenceScreen) findPreference("preferenceScreen");
            if (settings.getMnemonic().equals("")) {
                prefScreen.removePreference(backupPref);
            }

            Preference encryptionPref = (Preference) findPreference("encryption");
            Preference removeEncryptionPref = (Preference) findPreference("removeEncryption");
            removeEncryptionPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if (settings.getEncryptionType() == EncryptionType.PIN) {
                        Intent intent = new Intent(getContext(), PinActivity.class);
                        intent.putExtra("removePin", true);
                        startActivity(intent);
                    } else if (settings.getEncryptionType() == EncryptionType.FINGERPRINT) {
                        Intent intent = new Intent(getContext(), FingerprintSetupActivity.class);
                        intent.putExtra("removeFingerprint", true);
                        startActivity(intent);
                    }
                    return false;
                }
            });
            if (settings.getEncryptionType() == EncryptionType.UNENCRYPTED) {
                prefScreen.removePreference(removeEncryptionPref);
            } else {
                prefScreen.removePreference(encryptionPref);
            }

            Preference currencyPref = (Preference) findPreference("currency_preference");
            currencyPref.setSummary(settings.getFiatCurrency().toUpperCase());
            currencyPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    settings.setFiatCurrency(o.toString());
                    currencyPref.setSummary(o.toString().toUpperCase());
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

            EditTextPreference feePref = (EditTextPreference) findPreference("fee");
            feePref.setSummary(settings.getFeePerByte() + " sat/byte");
            feePref.setText(String.valueOf(settings.getFeePerByte()));
            feePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    settings.setFeePerByte(Integer.valueOf(o.toString()));
                    feePref.setSummary(Integer.valueOf(o.toString()) + " sat/byte");
                    feePref.setText(o.toString());
                    return false;
                }
            });

            Preference lovePref = (Preference) findPreference("love");
            //prefScreen.removePreference(lovePref);
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

            wallet.listenBlockchain(new WalletEventListener() {
                @Override
                public void onBlock(int blockHeight, String blockHash) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(new Runnable() {

                            @Override
                            public void run() {
                                String blockchainInfo = "Height: " + blockHeight + "\nHash: " + blockHash;
                                blockchainPref.setSummary(blockchainInfo);
                            }
                        });
                    }
                }
            });
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
        } else if (rootKey.equals("encryption")) {
            activeScreen = "encryption";
        }
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
            alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    wallet.rescan();
                }
            });
            alert.setCancelable(true);
            alert.show();
        } else {
            super.onDisplayPreferenceDialog(preference);
        }
    }
}