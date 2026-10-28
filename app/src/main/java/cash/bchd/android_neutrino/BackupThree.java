package cash.bchd.android_neutrino;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.Random;

import cash.bchd.android_neutrino.R;

public class BackupThree extends Fragment {

    public static Fragment newInstance(Context context) {
        cash.bchd.android_neutrino.BackupThree f = new cash.bchd.android_neutrino.BackupThree();

        return f;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.backup_three, container, false);

        Settings settings = Settings.getInstance();
        String mnemonic = settings.getMnemonic();
        String[] words = mnemonic.split("\\ ");

        int[] ns = quizIndexes(words.length, new Random());

        TextInputEditText enter1 = root.findViewById(R.id.enterWord1);
        TextInputEditText enter2 = root.findViewById(R.id.enterWord2);
        TextInputEditText enter3 = root.findViewById(R.id.enterWord3);
        TextInputLayout layout1 = root.findViewById(R.id.wordLayout1);
        TextInputLayout layout2 = root.findViewById(R.id.wordLayout2);
        TextInputLayout layout3 = root.findViewById(R.id.wordLayout3);

        enter1.setHint("Enter word " + (ns[0]+1));
        enter2.setHint("Enter word " + (ns[1]+1));
        enter3.setHint("Enter word " + (ns[2]+1));

        Button confirm = (Button) root.findViewById(R.id.confirmBtn);
        String errStr = "Incorrect word";
        confirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean error = false;
                if (matches(enter1.getText(), words[ns[0]])) {
                    layout1.setError(null);
                } else {
                    layout1.setError(errStr);
                    error = true;
                }
                if (matches(enter2.getText(), words[ns[1]])) {
                    layout2.setError(null);
                } else {
                    layout2.setError(errStr);
                    error = true;
                }
                if (matches(enter3.getText(), words[ns[2]])) {
                    layout3.setError(null);
                } else {
                    layout3.setError(errStr);
                    error = true;
                }
                if (error) {
                    return;
                }

                DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which){
                            case DialogInterface.BUTTON_POSITIVE:
                                settings.setMnemonic("");
                                Vibrator vibrator = (Vibrator)getContext().getSystemService(Context.VIBRATOR_SERVICE);
                                vibrator.vibrate(500);
                                BackupActivity.fa.finish();
                                break;

                            case DialogInterface.BUTTON_NEGATIVE:
                                //No button clicked
                                break;
                        }
                    }
                };

                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle("Are you sure?").setMessage("Once you continue your recovery phrase will be permanently deleted from this device.").setPositiveButton("Yes, delete", dialogClickListener)
                        .setNegativeButton("Cancel", dialogClickListener).show();
            }
        });

        return root;
    }

    /**
     * quizIndexes returns the positions of three different words of a phrase of wordCount words,
     * any of them, for the user to enter.
     */
    static int[] quizIndexes(int wordCount, Random random) {
        ArrayList<Integer> list = new ArrayList<Integer>();
        for (int i = 0; i < wordCount; i++) {
            list.add(i);
        }
        Collections.shuffle(list, random);
        return new int[]{list.get(0), list.get(1), list.get(2)};
    }

    /**
     * matches returns whether the word was entered, whatever its case and the spaces around it,
     * such as the one a keyboard adds after a word it suggested.
     */
    static boolean matches(CharSequence typed, String word) {
        return String.valueOf(typed).trim().toLowerCase(Locale.ROOT).equals(word);
    }
}
