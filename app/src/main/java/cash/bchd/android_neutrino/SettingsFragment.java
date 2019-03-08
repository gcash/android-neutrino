package cash.bchd.android_neutrino;

import android.os.Bundle;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;

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

        Preference blockchainPref = (Preference) findPreference("blockchain");
        String blockchainInfo = "Height: " + this.settings.getLastBlockHeight() + "\nHash: " + this.settings.getLastBlockHash();
        blockchainPref.setSummary(blockchainInfo);

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
    }
}