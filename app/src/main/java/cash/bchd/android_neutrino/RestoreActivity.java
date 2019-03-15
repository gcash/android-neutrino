package cash.bchd.android_neutrino;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.PreferenceScreen;
import android.support.design.widget.TextInputEditText;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

import java.io.File;

import cash.bchd.android_neutrino.wallet.Wallet;

public class RestoreActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recover);

        TextInputLayout layout1 = (TextInputLayout) findViewById(R.id.restoreLayout1);
        TextInputEditText input1 = (TextInputEditText) findViewById(R.id.restoreWord1);

        TextInputLayout layout2 = (TextInputLayout) findViewById(R.id.restoreLayout2);
        TextInputEditText input2 = (TextInputEditText) findViewById(R.id.restoreWord2);

        TextInputLayout layout3 = (TextInputLayout) findViewById(R.id.restoreLayout3);
        TextInputEditText input3 = (TextInputEditText) findViewById(R.id.restoreWord3);

        TextInputLayout layout4 = (TextInputLayout) findViewById(R.id.restoreLayout4);
        TextInputEditText input4 = (TextInputEditText) findViewById(R.id.restoreWord4);

        TextInputLayout layout5 = (TextInputLayout) findViewById(R.id.restoreLayout5);
        TextInputEditText input5 = (TextInputEditText) findViewById(R.id.restoreWord5);

        TextInputLayout layout6 = (TextInputLayout) findViewById(R.id.restoreLayout6);
        TextInputEditText input6 = (TextInputEditText) findViewById(R.id.restoreWord6);

        TextInputLayout layout7 = (TextInputLayout) findViewById(R.id.restoreLayout7);
        TextInputEditText input7 = (TextInputEditText) findViewById(R.id.restoreWord7);

        TextInputLayout layout8 = (TextInputLayout) findViewById(R.id.restoreLayout8);
        TextInputEditText input8 = (TextInputEditText) findViewById(R.id.restoreWord8);

        TextInputLayout layout9 = (TextInputLayout) findViewById(R.id.restoreLayout9);
        TextInputEditText input9 = (TextInputEditText) findViewById(R.id.restoreWord9);

        TextInputLayout layout10 = (TextInputLayout) findViewById(R.id.restoreLayout10);
        TextInputEditText input10 = (TextInputEditText) findViewById(R.id.restoreWord10);

        TextInputLayout layout11 = (TextInputLayout) findViewById(R.id.restoreLayout11);
        TextInputEditText input11 = (TextInputEditText) findViewById(R.id.restoreWord11);

        TextInputLayout layout12 = (TextInputLayout) findViewById(R.id.restoreLayout12);
        TextInputEditText input12 = (TextInputEditText) findViewById(R.id.restoreWord12);

        Button confirmBtn = (Button) findViewById(R.id.restoreConfirmBtn);
        confirmBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean error = false;
                String errStr = "Enter word";
                if (input1.getText().toString().equals("")) {
                    layout1.setError(errStr);
                    error = true;
                } else {
                    layout1.setError("");
                }
                if (input2.getText().toString().equals("")) {
                    layout2.setError(errStr);
                    error = true;
                } else {
                    layout2.setError("");
                }
                if (input3.getText().toString().equals("")) {
                    layout3.setError(errStr);
                    error = true;
                } else {
                    layout3.setError("");
                }
                if (input4.getText().toString().equals("")) {
                    layout4.setError(errStr);
                    error = true;
                } else {
                    layout4.setError("");
                }
                if (input5.getText().toString().equals("")) {
                    layout5.setError(errStr);
                    error = true;
                } else {
                    layout5.setError("");
                }
                if (input6.getText().toString().equals("")) {
                    layout6.setError(errStr);
                    error = true;
                } else {
                    layout6.setError("");
                }
                if (input7.getText().toString().equals("")) {
                    layout7.setError(errStr);
                    error = true;
                } else {
                    layout7.setError("");
                }
                if (input8.getText().toString().equals("")) {
                    layout8.setError(errStr);
                    error = true;
                } else {
                    layout8.setError("");
                }
                if (input9.getText().toString().equals("")) {
                    layout9.setError(errStr);
                    error = true;
                } else {
                    layout9.setError("");
                }
                if (input10.getText().toString().equals("")) {
                    layout10.setError(errStr);
                    error = true;
                } else {
                    layout10.setError("");
                }
                if (input11.getText().toString().equals("")) {
                    layout11.setError(errStr);
                    error = true;
                } else {
                    layout11.setError("");
                }
                if (input12.getText().toString().equals("")) {
                    layout12.setError(errStr);
                    error = true;
                } else {
                    layout12.setError("");
                }

                if (error) {
                    return;
                }

                DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which){
                            case DialogInterface.BUTTON_POSITIVE:
                                String mnemonic = input1.getText().toString() + " " +
                                        input2.getText().toString() + " " +
                                        input3.getText().toString() + " " +
                                        input4.getText().toString() + " " +
                                        input5.getText().toString() + " " +
                                        input6.getText().toString() + " " +
                                        input7.getText().toString() + " " +
                                        input8.getText().toString() + " " +
                                        input9.getText().toString() + " " +
                                        input10.getText().toString() + " " +
                                        input11.getText().toString() + " " +
                                        input12.getText().toString();
                                Settings settings = Settings.getInstance();
                                settings.setMnemonic(mnemonic);
                                settings.setWalletInitialized(false);

                                deleteRecursive(new File(getDataDir().getPath()));

                                Vibrator vibrator = (Vibrator)getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
                                vibrator.vibrate(500);
                                Wallet.getInstance().stop();

                                finish();
                                break;

                            case DialogInterface.BUTTON_NEGATIVE:
                                //No button clicked
                                break;
                        }
                    }
                };

                AlertDialog.Builder builder = new AlertDialog.Builder(RestoreActivity.this);
                builder.setTitle("Are you sure?").setMessage("Once you continue the existing wallet will be overridden with the new wallet and any coins in the existing wallet will be lost. Please backup the existing wallet if you need to do so before continuing.").setPositiveButton("Yes, restore", dialogClickListener)
                        .setNegativeButton("Cancel", dialogClickListener).show();
            }
        });
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                deleteRecursive(child);

        fileOrDirectory.delete();
    }

}
