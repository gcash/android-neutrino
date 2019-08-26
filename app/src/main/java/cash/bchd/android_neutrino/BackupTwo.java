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
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.backup_two, null);

        Settings settings = Settings.getInstance();
        String mnemonic = settings.getMnemonic();
        String[] words = mnemonic.split("\\ ");

        for (int i = 0; i < 12; i++) {
            TextView wordView = root.findViewById(R.id.word1);
            switch (i) {
                case 0:
                    wordView = root.findViewById(R.id.word1);
                    break;
                case 1:
                    wordView = root.findViewById(R.id.word2);
                    break;
                case 2:
                    wordView = root.findViewById(R.id.word3);
                    break;
                case 3:
                    wordView = root.findViewById(R.id.word4);
                    break;
                case 4:
                    wordView = root.findViewById(R.id.word5);
                    break;
                case 5:
                    wordView = root.findViewById(R.id.word6);
                    break;
                case 6:
                    wordView = root.findViewById(R.id.word7);
                    break;
                case 7:
                    wordView = root.findViewById(R.id.word8);
                    break;
                case 8:
                    wordView = root.findViewById(R.id.word9);
                    break;
                case 9:
                    wordView = root.findViewById(R.id.word10);
                    break;
                case 10:
                    wordView = root.findViewById(R.id.word11);
                    break;
                case 11:
                    wordView = root.findViewById(R.id.word12);
            }
            int n = i + 1;
            String first = "<font color='#8e8e92'>" + n + ". </font>";
            String next = "<font color='#000'><strong>" + words[i] + "</strong></font>";
            wordView.setText(Html.fromHtml(first + next));
        }
        return root;
    }

}