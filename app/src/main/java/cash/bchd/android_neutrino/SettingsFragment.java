package cash.bchd.android_neutrino;

import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.SwitchPreferenceCompat;

import cash.bchd.android_neutrino.wallet.Wallet;
import cash.bchd.android_neutrino.wallet.WalletEventListener;

public class SettingsFragment extends PreferenceFragmentCompat {

    public static final String FRAGMENT_TAG = "my_preference_fragment";

    Settings settings;
    Wallet wallet;

    public SettingsFragment() {

    }

    public void setSettings(Settings settings) {
        this.settings = settings;
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        settings = Settings.getInstance();
        wallet = Wallet.getInstance();

        if (rootKey == null) {
            if (settings == null) {
                settings = Settings.getInstance();
            }
            Preference blockchainPref = (Preference) findPreference("blockchain");
            String blockchainInfo = "Height: " + this.settings.getLastBlockHeight() + "\nHash: " + this.settings.getLastBlockHash();
            blockchainPref.setSummary(blockchainInfo);

            Preference backupPref = (Preference) findPreference("backup");
            PreferenceScreen prefScreen = (PreferenceScreen) findPreference("preferenceScreen");
            if (settings.getMnemonic().equals("")) {
                prefScreen.removePreference(backupPref);
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

            wallet.listenBlockchain(new WalletEventListener() {
                @Override
                public void onBlock(int blockHeight, String blockHash) {
                    getActivity().runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            String blockchainInfo = "Height: " + blockHeight + "\nHash: " + blockHash;
                            blockchainPref.setSummary(blockchainInfo);
                        }
                    });
                }
            });
        } else if (rootKey.equals("bchd")) {
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
}