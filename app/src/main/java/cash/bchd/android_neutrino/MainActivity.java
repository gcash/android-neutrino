package cash.bchd.android_neutrino;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URL;

import cash.bchd.android_neutrino.wallet.Config;
import cash.bchd.android_neutrino.wallet.Wallet;
import cash.bchd.android_neutrino.wallet.WalletReadyListener;

public class MainActivity extends AppCompatActivity {

    Wallet wallet;
    Settings settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        this.settings = new Settings(sharedPref);

        String[] addrs = new String[0];
        Config cfg = new Config(getDataDir().getPath(), !settings.getWalletInitialized(),
                true, settings.getBlocksOnly(), addrs, "", "",
                "", "");
        this.wallet = new Wallet(this, cfg);

        new StartWalletTask().execute(wallet);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private class StartWalletTask extends AsyncTask<Wallet, Void, String> {
        Wallet wallet;
        protected String doInBackground(Wallet... wallets) {
            wallet = wallets[0];
            wallets[0].start();
            return "";
        }
        protected void onPostExecute(String result) {
            try {
                wallet.loadWallet(new WalletReadyListener() {
                    @Override
                    public void walletReady() {
                        System.out.println("Wallet ready");
                        settings.setWalletInitialized(true);
                        try {
                            long bal = wallet.balance();
                            System.out.println(bal);
                        } catch (Exception e) {}
                    }

                    @Override
                    public void setMnemonicSeed(String seed) {
                        settings.setMnemonic(seed);
                    }
                });
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }
}
