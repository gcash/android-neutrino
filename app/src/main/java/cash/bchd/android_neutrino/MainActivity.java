package cash.bchd.android_neutrino;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.transition.ChangeTransform;
import android.transition.TransitionManager;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.google.android.gms.common.api.CommonStatusCodes;

import java.util.Collections;
import java.util.Currency;
import java.util.List;

import androidmads.library.qrgenearator.QRGContents;
import androidmads.library.qrgenearator.QRGEncoder;
import cash.bchd.android_neutrino.wallet.AddressListener;
import cash.bchd.android_neutrino.wallet.Amount;
import cash.bchd.android_neutrino.wallet.Config;
import cash.bchd.android_neutrino.wallet.ExchangeRates;
import cash.bchd.android_neutrino.wallet.TransactionData;
import cash.bchd.android_neutrino.wallet.Wallet;
import cash.bchd.android_neutrino.wallet.WalletEventListener;


public class MainActivity extends CloseActivity {

    Settings settings;
    ExchangeRates exchangeRates;
    FloatingActionButton fab;
    FloatingActionButton fabSettings;
    FloatingActionButton fabReceive;
    FloatingActionButton fabSend;
    FloatingActionButton fabScan;
    FloatingActionButton fabQR;
    boolean isFabOpen;
    long lastDown;
    CoordinatorLayout mCLayout;
    ImageView qrImage;
    TextView addrText;
    TransactionStore txStore;
    RecyclerView.LayoutManager layoutManager;
    TransactionAdapter mAdapter;
    SwipeRefreshLayout mSwipeRefreshLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cancelCloseTimer();

        layoutManager = new LinearLayoutManager(this);

        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        this.settings = new Settings(sharedPref);
        this.exchangeRates = new ExchangeRates();
        this.txStore = new TransactionStore(this);

        TextView bchBalanceView = (TextView)findViewById(R.id.bchBalanceView);
        Amount lastBal = new Amount(this.settings.getLastBalance());
        bchBalanceView.setText(lastBal.toString() + " BCH");

        Thread thread = new Thread() {
            public void run() {
                String fiatCurrency = settings.getFiatCurrency();
                try {
                    exchangeRates.fetchFormattedAmountInFiat(lastBal, Currency.getInstance(fiatCurrency), new ExchangeRates.Callback() {
                        @Override
                        public void onRateFetched(String formatted) {
                            TextView fiatBalanceView = (TextView) findViewById(R.id.fiatBalanceView);
                            fiatBalanceView.setText(formatted);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();

        if (Wallet.getInstance() == null) {
            String[] addrs = new String[0];
            String bchdIP = settings.getBchdIP();
            Config cfg = new Config(getDataDir().getPath(), !settings.getWalletInitialized(),
                    bchdIP.equals(""), settings.getBlocksOnly(), addrs, settings.getBchdIP(), settings.getBchdUsername(),
                    settings.getBchdPassword(), settings.getBchdCert());
            wallet = new Wallet(this, cfg);
            new StartWalletTask().execute(wallet);
        }

        fab = findViewById(R.id.fab);
        fabSettings = findViewById(R.id.btnSettings);
        fabReceive = findViewById(R.id.btnReceive);
        fabSend = findViewById(R.id.btnSend);
        fabScan = findViewById(R.id.btnScan);
        fabQR = findViewById(R.id.btnQR);


        mCLayout = (CoordinatorLayout) findViewById(R.id.coordinator_layout);
        ChangeTransform changeTransform = new ChangeTransform();
        changeTransform.setDuration(500);
        changeTransform.setInterpolator(new AccelerateInterpolator());
        TransitionManager.beginDelayedTransition(mCLayout,changeTransform);

        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.txRecylerView);
        recyclerView.setHasFixedSize(true);
        DividerItemDecoration decor = new DividerItemDecoration(getApplicationContext(), DividerItemDecoration.VERTICAL);
        recyclerView.addItemDecoration(decor);

        // use a linear layout manager

        recyclerView.setLayoutManager(layoutManager);

        // specify an adapter
        List<TransactionData> txs = txStore.getData();
        Collections.sort(txs, Collections.reverseOrder());
        if (txs.size() > 0) {
            TextView bchPlease = (TextView) findViewById(R.id.bchPlease);
            bchPlease.setVisibility(View.GONE);
        }
        mAdapter = new TransactionAdapter(txs, this, mCLayout, settings.getLastBlockHeight());
        recyclerView.setAdapter(mAdapter);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
               toggleFABMenu();
            }
        });

        fab.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN) {
                    toggleFABMenu();
                    lastDown = System.currentTimeMillis();
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    toggleFABMenu();
                    if (System.currentTimeMillis() - lastDown < 500) {
                        v.performClick();
                    } else {
                        if (inViewInBounds(fabQR, (int) event.getRawX(), (int) event.getRawY())) {
                            displayQRPopup();
                            toggleFABMenu();
                        }
                        if (inViewInBounds(fabScan, (int) event.getRawX(), (int) event.getRawY())) {
                            displayQRScanner();
                            toggleFABMenu();
                        }
                        if (inViewInBounds(fabSend, (int) event.getRawX(), (int) event.getRawY())) {
                            openSendActivity();
                            toggleFABMenu();
                        }
                        if (inViewInBounds(fabReceive, (int) event.getRawX(), (int) event.getRawY())) {
                            openReceiveActivity();
                            toggleFABMenu();
                        }
                        if (inViewInBounds(fabSettings, (int) event.getRawX(), (int) event.getRawY())) {
                            openSettingsActivity();
                            toggleFABMenu();
                        }
                    }
                }
                return true;
            }
        });

        fabQR.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fab.bringToFront();
                displayQRPopup();
                sendViewToBack(v);
            }
        });

        fabScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fab.bringToFront();
                displayQRScanner();
            }
        });

        fabSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fab.bringToFront();
                openSendActivity();
            }
        });

        fabReceive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fab.bringToFront();
                openReceiveActivity();
            }
        });

        fabSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fab.bringToFront();
                openSettingsActivity();
            }
        });

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipeRefreshLayout);
    }

    @Override
    protected void onStart() {
        super.onStart();
        cancelCloseTimer();
    }

    Rect outRect = new Rect();
    int[] location = new int[2];


    private boolean inViewInBounds(View view, int x, int y) {
        view.getDrawingRect(outRect);
        view.getLocationOnScreen(location);
        outRect.offset(location[0], location[1]);
        return outRect.contains(x, y);
    }

    private void openSendActivity() {
        toggleFABMenu();
        Intent intent = new Intent(this, SendActivity.class);
        intent.putExtra("fiatCurrency", this.settings.getFiatCurrency());
        intent.putExtra("feePerByte", this.settings.getFeePerByte());
        startActivity(intent);
    }

    private void openReceiveActivity() {
        toggleFABMenu();
        Intent intent = new Intent(this, ReceiveActivity.class);
        intent.putExtra("fiatCurrency", this.settings.getFiatCurrency());
        intent.putExtra("lastAddress", this.settings.getLastAddress());
        intent.putExtra("defaultLabel", this.settings.getDefaultLabel());
        intent.putExtra("defaultMemo", this.settings.getDefaultMemo());
        startActivity(intent);
    }

    private void openSettingsActivity() {
        toggleFABMenu();
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    private void displayQRScanner() {
        toggleFABMenu();
        Intent intent = new Intent(this, ScannerActivity.class);
        startActivityForResult(intent, SendActivity.RC_BARCODE_CAPTURE);
    }

    private void displayQRPopup() {
        String lastAddr = "";
        if (!wallet.isRunning()) {
            lastAddr = settings.getLastAddress();
            if (lastAddr.equals("")) {
                Snackbar snackbar = Snackbar.make(mCLayout, "Wallet isn't loaded yet.", Snackbar.LENGTH_LONG);
                snackbar.show();
                return;
            }
        }
        toggleFABMenu();
        LayoutInflater layoutInflater = (LayoutInflater) MainActivity.this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View customView = layoutInflater.inflate(R.layout.qrpopup,null);
        PopupWindow popupWindow = new PopupWindow(customView, CoordinatorLayout.LayoutParams.WRAP_CONTENT, CoordinatorLayout.LayoutParams.WRAP_CONTENT, true);
        popupWindow.showAtLocation(findViewById(R.id.coordinator_layout), Gravity.CENTER, 0, 0);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        try {
            String addr;
            if (wallet.isRunning()) {
                addr = wallet.currentAddress();
                settings.setLastAddress(addr);
            } else {
                addr = lastAddr;
            }
            WindowManager manager = (WindowManager) getSystemService(WINDOW_SERVICE);
            Display display = manager.getDefaultDisplay();
            Point point = new Point();
            display.getSize(point);
            int width = point.x;
            int height = point.y;
            int smallerDimension = width < height ? width : height;
            smallerDimension = smallerDimension;

            String addrURI = wallet.uriPrefix() + addr;
            QRGEncoder qrgEncoder = new QRGEncoder(
                    addrURI, null,
                    QRGContents.Type.TEXT,
                    smallerDimension);
            Bitmap bitmap = qrgEncoder.encodeAsBitmap();
            qrImage = (ImageView) customView.findViewById(R.id.qrCodeView);
            qrImage.setImageBitmap(bitmap);

            qrImage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    copyToClipboard(addr);
                }
            });

            addrText = (TextView) customView.findViewById(R.id.address);
            addrText.setText(addr);
            addrText.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                   copyToClipboard(addr);
                }
            });

            wallet.listenAddress(addr, new AddressListener() {
                @Override
                public void onPaymentReceived(long amount) {
                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            Vibrator vibrator = (Vibrator)getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
                            vibrator.vibrate(500);
                            LinearLayout qrLayout = (LinearLayout) customView.findViewById(R.id.qrCodeLayout);
                            int h = qrLayout.getHeight();
                            qrLayout.setHorizontalGravity(Gravity.CENTER_HORIZONTAL);
                            qrLayout.setVerticalGravity(Gravity.CENTER_VERTICAL);

                            TextView addrHelperText = (TextView) customView.findViewById(R.id.addrHelpText);

                            qrImage.setVisibility(View.GONE);
                            addrText.setVisibility(View.GONE);
                            addrHelperText.setVisibility(View.GONE);
                            final GifView showGifView = new GifView(getApplicationContext());

                            showGifView.setGifImageDrawableId(R.drawable.coinflip);
                            showGifView.drawGif();
                            showGifView.setForegroundGravity(Gravity.CENTER);


                            ViewGroup.LayoutParams params = qrLayout.getLayoutParams();
                            Double dh = new Double(h);
                            Double truncatedH = dh * 0.8;
                            params.height = truncatedH.intValue();
                            params.width = h;

                            qrLayout.requestLayout();
                            qrLayout.addView(showGifView);
                        }
                    });
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void copyToClipboard(String data) {
        Object clipboardService = getSystemService(CLIPBOARD_SERVICE);
        final ClipboardManager clipboardManager = (ClipboardManager)clipboardService;
        ClipData clipData = ClipData.newPlainText("Source Text", data);
        clipboardManager.setPrimaryClip(clipData);
        Snackbar snackbar = Snackbar.make(mCLayout, "Address copied clipboard.", Snackbar.LENGTH_LONG);
        snackbar.show();
    }

    private void toggleFABMenu() {
        toggleRotation(fab);
        if(!isFabOpen){
            showFABMenu();
        }else{
            closeFABMenu();
        }
    }

    private void showFABMenu(){
        isFabOpen = true;
        fabSettings.animate().translationY(-getResources().getDimension(R.dimen.standard_65));
        fabReceive.animate().translationY(-getResources().getDimension(R.dimen.standard_120));
        fabSend.animate().translationY(-getResources().getDimension(R.dimen.standard_175));
        fabScan.animate().translationY(-getResources().getDimension(R.dimen.standard_230));
        fabQR.animate().translationY(-getResources().getDimension(R.dimen.standard_285));
    }

    private void closeFABMenu(){
        isFabOpen = false;
        fabSettings.animate().translationY(0);
        fabReceive.animate().translationY(0);
        fabSend.animate().translationY(0);
        fabScan.animate().translationY(0);
        fabQR.animate().translationY(0);
        fab.bringToFront();
    }

    protected void toggleRotation(View v){
        if(isFabOpen){
            v.setRotation(0.0f);
        }else {
            v.setRotation(45.0f);
        }
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
                WalletEventListener listener = new WalletEventListener() {
                    @Override
                    public void onWalletReady() {
                        System.out.println("Wallet ready");
                    }

                    @Override
                    public void onBalanceChange(long bal) {
                        try {
                            System.out.println("Updating balance");
                            Amount amt = new Amount(bal);
                            TextView bchBalanceView = (TextView) findViewById(R.id.bchBalanceView);
                            String balanceStr = amt.toString() + " BCH";
                            bchBalanceView.setText(balanceStr);
                            String fiatAmount = exchangeRates.getFormattedAmountInFiat(amt, Currency.getInstance(settings.getFiatCurrency()));
                            TextView fiatBalanceView = (TextView) findViewById(R.id.fiatBalanceView);
                            fiatBalanceView.setText(fiatAmount);
                            settings.setLastBalance(bal);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onWalletCreated(String seed) {
                        settings.setWalletInitialized(true);
                        settings.setMnemonic(seed);
                    }

                    @Override
                    public void onGetTransactions(List<TransactionData> txs, int blockHeight) {
                        Collections.sort(txs, Collections.reverseOrder());
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (txs.size() == 0) {
                                    return;
                                }
                                TextView bchPlease = (TextView) findViewById(R.id.bchPlease);
                                bchPlease.setVisibility(View.GONE);
                                for (TransactionData tx : txs) {
                                    String fiatCurrency = settings.getFiatCurrency();
                                    tx.setFiatCurrency(fiatCurrency);
                                    String formattedFiat = "";
                                    try {
                                        formattedFiat = exchangeRates.getFormattedAmountInFiat(new Amount(tx.getAmount()), Currency.getInstance(fiatCurrency));
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                    tx.setFiatAmount(formattedFiat);
                                    mAdapter.updateOrInsertTx(tx);
                                }
                                mAdapter.notifyDataSetChanged();
                                txStore.setData(mAdapter.getData());
                                try {
                                    txStore.save(getApplicationContext());
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                    }

                    @Override
                    public void onBlock(int blockHeight, String blockHash) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mAdapter.setBlockHeight(blockHeight);
                                mAdapter.notifyDataSetChanged();
                                settings.setLastBlockHeight(blockHeight);
                                settings.setLastBlockHash(blockHash);
                            }
                        });
                    }

                    @Override
                    public void onTransaction(TransactionData tx) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                TextView bchPlease = (TextView) findViewById(R.id.bchPlease);
                                bchPlease.setVisibility(View.GONE);

                                String fiatCurrency = settings.getFiatCurrency();
                                tx.setFiatCurrency(fiatCurrency);
                                String formattedFiat = "";
                                try {
                                    formattedFiat = exchangeRates.getFormattedAmountInFiat(new Amount(tx.getAmount()), Currency.getInstance(fiatCurrency));
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                tx.setFiatAmount(formattedFiat);
                                mAdapter.updateOrInsertTx(tx);
                                mAdapter.notifyDataSetChanged();

                                txStore.setData(mAdapter.getData());
                                try {
                                    txStore.save(getApplicationContext());
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                    }
                };
                wallet.loadWallet(listener);
                mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        try {
                            wallet.getTransactions(listener);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        mSwipeRefreshLayout.setRefreshing(false);
                    }
                });
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    public static void sendViewToBack(final View child) {
        final ViewGroup parent = (ViewGroup)child.getParent();
        if (null != parent) {
            parent.removeView(child);
            parent.addView(child, 0);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SendActivity.RC_BARCODE_CAPTURE) {
            if (resultCode == CommonStatusCodes.SUCCESS) {
                if (data != null) {
                    String qrdata = data.getStringExtra("qrdata");
                    Intent intent = new Intent(this, SendActivity.class);
                    intent.putExtra("fiatCurrency", this.settings.getFiatCurrency());
                    intent.putExtra("feePerByte", this.settings.getFeePerByte());
                    intent.putExtra("qrdata", qrdata);
                    startActivity(intent);
                }
            } else if (resultCode != 0 ) {
                Snackbar snackbar = Snackbar.make(mCLayout, "Barcode Read Error", Snackbar.LENGTH_LONG);
                snackbar.show();
            }
        }
        else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

}
