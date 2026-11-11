package cash.bchd.android_neutrino;

import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

public class BackupTwo extends Fragment {

    public static Fragment newInstance() {
        return new BackupTwo();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.backup_two, container, false);

        Settings settings = Settings.getInstance();
        String mnemonic = settings.getMnemonic();
        // A phrase whose key is gone can't be read, and there's nothing to write down.
        if (mnemonic.isEmpty()) {
            return keyMissing(inflater, container);
        }
        String[] words = mnemonic.split("\\ ");

        // A box for each word: a restored wallet's phrase has 12 to 24 of them.
        ViewGroup wordViews = root.findViewById(R.id.backupWords);
        for (int i = 0; i < words.length; i++) {
            TextView wordView = (TextView) inflater.inflate(R.layout.backup_word, wordViews, false);
            String first = "<font color='#8e8e92'>" + (i + 1) + ". </font>";
            String next = "<font color='#000'><strong>" + words[i] + "</strong></font>";
            wordView.setText(Html.fromHtml(first + next, Html.FROM_HTML_MODE_LEGACY));
            wordViews.addView(wordView);
        }
        return root;
    }

    /**
     * keyMissing returns what the backup's screens show when the phrase can't be read, as its
     * key is gone: that the wallet is to be restored from its phrase.
     */
    static View keyMissing(LayoutInflater inflater, ViewGroup container) {
        TextView missing = (TextView) inflater.inflate(R.layout.backup_word, container, false);
        missing.setText(R.string.wallet_key_missing);
        return missing;
    }
}