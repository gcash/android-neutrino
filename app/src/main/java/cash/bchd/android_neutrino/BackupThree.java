package cash.bchd.android_neutrino;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.design.widget.TextInputEditText;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

import cash.bchd.android_neutrino.R;

public class BackupThree extends Fragment {

    public static Fragment newInstance(Context context) {
        cash.bchd.android_neutrino.BackupThree f = new cash.bchd.android_neutrino.BackupThree();

        return f;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.backup_three, null);

        Settings settings = Settings.getInstance();
        String mnemonic = settings.getMnemonic();
        String[] words = mnemonic.split("\\ ");

        ArrayList<Integer> list = new ArrayList<Integer>();
        for (int i=0; i<12; i++) {
            list.add(new Integer(i));
        }
        Collections.shuffle(list);
        int[] ns = new int[3];
        for (int i=0; i<3; i++) {
            ns[i] = list.get(i);
        }

        TextInputEditText enter1 = (TextInputEditText) root.findViewById(R.id.enterWord1);
        TextInputEditText enter2 = (TextInputEditText) root.findViewById(R.id.enterWord2);
        TextInputEditText enter3 = (TextInputEditText) root.findViewById(R.id.enterWord3);
        TextInputLayout layout1 = (TextInputLayout) root.findViewById(R.id.wordLayout1);
        TextInputLayout layout2 = (TextInputLayout) root.findViewById(R.id.wordLayout2);
        TextInputLayout layout3 = (TextInputLayout) root.findViewById(R.id.wordLayout3);

        enter1.setHint("Enter word " + (ns[0]+1));
        enter2.setHint("Enter word " + (ns[1]+1));
        enter3.setHint("Enter word " + (ns[2]+1));

        Button confirm = (Button) root.findViewById(R.id.confirmBtn);
        String errStr = "Incorrect word";
        confirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean error = false;
                if (enter1.getText().toString().equals(words[ns[0]])) {
                    layout1.setError(null);
                } else {
                    layout1.setError(errStr);
                    error = true;
                }
                if (enter2.getText().toString().equals(words[ns[1]])) {
                    layout2.setError(null);
                } else {
                    layout2.setError(errStr);
                    error = true;
                }
                if (enter3.getText().toString().equals(words[ns[2]])) {
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

}