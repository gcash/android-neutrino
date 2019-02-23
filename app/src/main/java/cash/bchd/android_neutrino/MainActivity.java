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
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.transition.ChangeTransform;
import android.transition.TransitionManager;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.Currency;

import androidmads.library.qrgenearator.QRGContents;
import androidmads.library.qrgenearator.QRGEncoder;
import cash.bchd.android_neutrino.wallet.AddressListener;
import cash.bchd.android_neutrino.wallet.Amount;
import cash.bchd.android_neutrino.wallet.Config;
import cash.bchd.android_neutrino.wallet.ExchangeRates;
import cash.bchd.android_neutrino.wallet.Wallet;
import cash.bchd.android_neutrino.wallet.WalletReadyListener;

public class MainActivity extends CloseActivity {

    Settings settings;
    ExchangeRates exchangeRates;
    FloatingActionButton fab;
    FloatingActionButton fab1;
    FloatingActionButton fab2;
    FloatingActionButton fab3;
    FloatingActionButton fabScan;
    FloatingActionButton fabQR;
    boolean isFabOpen;
    long lastDown;
    CoordinatorLayout mCLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        this.settings = new Settings(sharedPref);
        this.exchangeRates = new ExchangeRates();

        TextView bchBalanceView = (TextView)findViewById(R.id.bchBalanceView);
        bchBalanceView.setText(new Amount(this.settings.getLastBalance()).toString() + " BCH");

        String fiatCurrency = this.settings.getFiatCurrency();
        float lastFiatBalance = this.settings.getLastFiatBalance();

        String formattedFiatBalance = Currency.getInstance(fiatCurrency).getSymbol() + ExchangeRates.round(lastFiatBalance, 2);
        TextView fiatBalanceView = (TextView)findViewById(R.id.fiatBalanceView);
        fiatBalanceView.setText(formattedFiatBalance);

        if (wallet == null) {
            String[] addrs = new String[0];
            Config cfg = new Config(getDataDir().getPath(), !settings.getWalletInitialized(),
                    true, settings.getBlocksOnly(), addrs, "", "",
                    "", "");
            wallet = new Wallet(this, cfg);
            new StartWalletTask().execute(wallet);
        }

        fab = findViewById(R.id.fab);
        fab1 = findViewById(R.id.speakNowFab);
        fab2 = findViewById(R.id.speakNowFab1);
        fab3 = findViewById(R.id.speakNowFab2);
        fabScan = findViewById(R.id.btnScan);
        fabQR = findViewById(R.id.btnQR);


        mCLayout = (CoordinatorLayout) findViewById(R.id.coordinator_layout);
        ChangeTransform changeTransform = new ChangeTransform();
        changeTransform.setDuration(500);
        changeTransform.setInterpolator(new AccelerateInterpolator());
        TransitionManager.beginDelayedTransition(mCLayout,changeTransform);

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
                    }
                }
                return true;
            }
        });

        fabQR.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                displayQRPopup();
            }
        });

        fabScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                displayQRScanner();
            }
        });

    }

    Rect outRect = new Rect();
    int[] location = new int[2];


    private boolean inViewInBounds(View view, int x, int y) {
        view.getDrawingRect(outRect);
        view.getLocationOnScreen(location);
        outRect.offset(location[0], location[1]);
        return outRect.contains(x, y);
    }

    private void displayQRScanner() {
        toggleFABMenu();
        Intent intent = new Intent(this, ScannerActivity.class);
        startActivity(intent);
    }

    private void displayQRPopup() {
        if (!wallet.isRunning()) {
            Snackbar snackbar = Snackbar.make(mCLayout, "Wallet isn't loaded yet.", Snackbar.LENGTH_LONG);
            snackbar.show();
            return;
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
            String addr = wallet.currentAddress();
            WindowManager manager = (WindowManager) getSystemService(WINDOW_SERVICE);
            Display display = manager.getDefaultDisplay();
            Point point = new Point();
            display.getSize(point);
            int width = point.x;
            int height = point.y;
            int smallerDimension = width < height ? width : height;
            smallerDimension = smallerDimension;

            QRGEncoder qrgEncoder = new QRGEncoder(
                    addr, null,
                    QRGContents.Type.TEXT,
                    smallerDimension);
            Bitmap bitmap = qrgEncoder.encodeAsBitmap();
            ImageView qrImage = (ImageView) customView.findViewById(R.id.qrCodeView);
            qrImage.setImageBitmap(bitmap);

            qrImage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    copyToClipboard(addr);
                }
            });

            TextView addrText = (TextView) customView.findViewById(R.id.address);
            addrText.setText(addr);
            addrText.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                   copyToClipboard(addr);
                }
            });

            wallet.listenAddress(addr, new AddressListener() {
                @Override
                public void onPaymentReceived() {
                    LinearLayout qrLayout = (LinearLayout) customView.findViewById(R.id.qrCodeLayout);
                    int h = qrLayout.getHeight();

                    qrImage.setVisibility(View.GONE);
                    addrText.setVisibility(View.GONE);
                    final GifView showGifView = new GifView(getApplicationContext());
                    showGifView.setGifImageDrawableId(R.drawable.coinflip);
                    showGifView.drawGif();

                    ViewGroup.LayoutParams params = qrLayout.getLayoutParams();
                    params.height = h;
                    params.width = h;
                    qrLayout.requestLayout();
                    qrLayout.addView(showGifView);
                    qrLayout.setGravity(Gravity.CENTER);
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
        fab1.animate().translationY(-getResources().getDimension(R.dimen.standard_65));
        fab2.animate().translationY(-getResources().getDimension(R.dimen.standard_120));
        fab3.animate().translationY(-getResources().getDimension(R.dimen.standard_175));
        fabScan.animate().translationY(-getResources().getDimension(R.dimen.standard_230));
        fabQR.animate().translationY(-getResources().getDimension(R.dimen.standard_285));
    }

    private void closeFABMenu(){
        isFabOpen = false;
        fab1.animate().translationY(0);
        fab2.animate().translationY(0);
        fab3.animate().translationY(0);
        fabScan.animate().translationY(0);
        fabQR.animate().translationY(0);
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
                wallet.loadWallet(new WalletReadyListener() {
                    @Override
                    public void walletReady() {
                        System.out.println("Wallet ready");
                        settings.setWalletInitialized(true);
                        try {
                            long bal = wallet.balance();
                            settings.setLastBalance(bal);
                            Amount amt = new Amount(bal);
                            TextView bchBalanceView = (TextView)findViewById(R.id.bchBalanceView);
                            bchBalanceView.setText(amt.toString() + " BCH");
                            exchangeRates.getFormattedAmountInFiat(amt, Currency.getInstance(settings.getFiatCurrency()));
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
