package cash.bchd.android_neutrino;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.TextUtils;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import cash.bchd.android_neutrino.wallet.Bip39;
import cash.bchd.android_neutrino.wallet.Wallet;

public class RestoreActivity extends AppCompatActivity {

    // August 1, 2017, when Bitcoin Cash split from Bitcoin. The wallet finds coins only in the
    // blocks from its birthday on, so a phrase that received coins before then needs an earlier
    // birthday.
    static final long DEFAULT_BIRTHDAY = 1501545600;
    // The time of the first block, on January 3, 2009.
    static final long FIRST_BLOCK_TIME = 1231006505;

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    // The birthday of the restored wallet, in seconds.
    private long birthday = DEFAULT_BIRTHDAY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recover);
        SystemBars.apply(this);
        // Keep the recovery phrase and PIN out of screenshots and the recent apps.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        if (savedInstanceState != null) {
            birthday = savedInstanceState.getLong("birthday", DEFAULT_BIRTHDAY);
        }

        TextInputLayout phraseLayout = findViewById(R.id.restorePhraseLayout);
        TextInputEditText phraseInput = findViewById(R.id.restorePhrase);
        TextView birthdayView = findViewById(R.id.restoreBirthday);
        showBirthday(birthdayView);
        birthdayView.setOnClickListener(v -> pickBirthday(birthdayView));

        Button confirmBtn = findViewById(R.id.restoreConfirmBtn);
        confirmBtn.setOnClickListener(v -> {
            // Check the phrase as bchwallet will before the current wallet is deleted.
            String phrase = Bip39.normalize(String.valueOf(phraseInput.getText()));
            Bip39.Check check = Bip39.check(phrase);
            switch (check.problem) {
                case WORD_COUNT:
                    phraseLayout.setError(getString(R.string.restore_word_count));
                    return;
                case UNKNOWN_WORDS:
                    phraseLayout.setError(getString(R.string.restore_unknown_words, TextUtils.join(", ", check.unknownWords)));
                    return;
                case CHECKSUM:
                    phraseLayout.setError(getString(R.string.restore_checksum));
                    return;
                default:
                    phraseLayout.setError(null);
            }

            new AlertDialog.Builder(RestoreActivity.this)
                    .setTitle("Are you sure?")
                    .setMessage("Once you continue the existing wallet will be overridden with the new wallet and any coins in the existing wallet will be lost. Please backup the existing wallet if you need to do so before continuing.")
                    .setPositiveButton("Yes, restore", (dialog, which) ->
                            // Restoring deletes the current wallet, so it asks for the screen
                            // lock as sending does.
                            ScreenLock.confirm(RestoreActivity.this, getString(R.string.screen_lock_restore), new ScreenLock.Callback() {
                                @Override
                                public void onConfirmed() {
                                    restore(phrase);
                                }

                                @Override
                                public void onCancelled() {
                                }
                            }))
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong("birthday", birthday);
    }

    private void showBirthday(TextView view) {
        DateFormat format = DateFormat.getDateInstance(DateFormat.LONG);
        format.setTimeZone(UTC);
        view.setText(format.format(new Date(birthday * 1000)));
    }

    private void pickBirthday(TextView view) {
        Calendar current = Calendar.getInstance(UTC);
        current.setTimeInMillis(birthday * 1000);
        DatePickerDialog dialog = new DatePickerDialog(this, (picker, year, month, day) -> {
            Calendar picked = Calendar.getInstance(UTC);
            picked.clear();
            picked.set(year, month, day);
            birthday = Math.max(picked.getTimeInMillis() / 1000, FIRST_BLOCK_TIME);
            showBirthday(view);
        }, current.get(Calendar.YEAR), current.get(Calendar.MONTH), current.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMinDate(FIRST_BLOCK_TIME * 1000);
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    private void restore(String phrase) {
        Settings settings = Settings.getInstance();
        settings.setMnemonic(phrase);
        settings.setWalletInitialized(false);
        settings.setWalletBirthday(birthday);
        settings.setScanHeight(0);
        settings.setEncryptionType(EncryptionType.UNENCRYPTED);
        settings.setLastBalance(0);
        settings.setLastAddress("");
        settings.setBackupReminder(false);

        // Stop the wallet, and delete its files once it has closed them, so
        // that MainActivity creates the restored wallet. The cached
        // transactions are only read by the app and can go right away.
        deleteFile(TransactionStore.FILE_NAME);
        Wallet wallet = Wallet.getInstance();
        if (wallet != null) {
            wallet.stop();
        }
        File walletDir = new File(getDataDir(), "mainnet");
        Wallet.runAfterStop(() -> deleteRecursive(walletDir));

        Vibrator vibrator = (Vibrator) getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
        vibrator.vibrate(500);

        Intent intent = new Intent(RestoreActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private static void deleteRecursive(File fileOrDirectory) {
        File[] children = fileOrDirectory.listFiles();
        if (children != null)
            for (File child : children)
                deleteRecursive(child);

        fileOrDirectory.delete();
    }
}
