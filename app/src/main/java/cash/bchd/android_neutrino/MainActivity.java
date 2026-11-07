package cash.bchd.android_neutrino;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.Handler;
import android.text.TextUtils;
import android.transition.ChangeTransform;
import android.transition.TransitionManager;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.os.BundleCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.zxing.WriterException;

import java.lang.ref.WeakReference;
import java.text.NumberFormat;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;

import com.google.common.util.concurrent.Futures;

import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import cash.bchd.android_neutrino.tokens.TokenAdapter;
import cash.bchd.android_neutrino.tokens.NftArt;
import cash.bchd.android_neutrino.tokens.TokenIcons;
import cash.bchd.android_neutrino.tokens.TokenLabels;
import cash.bchd.android_neutrino.wallet.AddressListener;
import cash.bchd.android_neutrino.wallet.Addresses;
import cash.bchd.android_neutrino.wallet.Amount;
import cash.bchd.android_neutrino.wallet.BitcoinPaymentURI;
import cash.bchd.android_neutrino.wallet.ExchangeRates;
import cash.bchd.android_neutrino.wallet.Network;
import cash.bchd.android_neutrino.wallet.SyncStatus;
import cash.bchd.android_neutrino.wallet.TokenBalance;
import cash.bchd.android_neutrino.wallet.TokenChange;
import cash.bchd.android_neutrino.wallet.TokenLabelFile;
import cash.bchd.android_neutrino.wallet.TokenMeta;
import cash.bchd.android_neutrino.wallet.TransactionData;
import cash.bchd.android_neutrino.wallet.TransactionList;
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
    // Enabled while the menu is open, so that going back closes it instead of the app.
    final OnBackPressedCallback closeMenuOnBack = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            toggleFABMenu();
        }
    };
    long lastDown;
    CoordinatorLayout mCLayout;
    ImageView qrImage;
    TextView addrText;
    PopupWindow qrPopup;
    TransactionStore txStore;
    TransactionAdapter mAdapter;
    // Which icons the rows show, to tell when that changes.
    private String shownTokenIcons;
    TokenAdapter tokenAdapter;
    private static final int TOKEN_LABELS_PER_CALL = 50;
    private static final long TOKEN_LABELS_INTERVAL_MS = 10_000;
    private final Handler pagesHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean labelsAskPending = new AtomicBoolean();
    private NftArt iconLoader;
    // The labels the rows were drawn with, to tell when another screen learned more.
    private long drawnLabels;
    // The Activity and Tokens pages, and the tabs between them, which show while the wallet
    // holds tokens.
    MainPages mainPages;
    ViewPager2 pages;
    TabLayout tabs;
    // The wallet this activity listens to the events of, which it started or found running, and
    // the listener it listens with.
    private Wallet wallet;
    private WalletEventListener eventListener;
    // Shows the wallet's sync status while this activity is in front.
    private ScheduledExecutorService syncStatusPoller;
    // Set while the user is asked to let the app reach the local network, which the wallet's
    // start waits for.
    private boolean askingForLocalNetwork;

    private static final long DONATION_REMINDER_INTERVAL = TimeUnit.DAYS.toMillis(45);
    private static final long SYNC_STATUS_POLL_MILLIS = 2000;
    private static final int RC_LOCAL_NETWORK = 9005;
    // The popup a recreated screen shows again: the address's QR code, or a transaction's details.
    private static final String STATE_QR_POPUP = "qrPopup";
    private static final String STATE_TX_DETAILS = "txDetails";

    // The scanner, whose QR code opens as a payment link.
    private final ActivityResultLauncher<Void> scanner =
            registerForActivityResult(new ScannerActivity.Scan(), qrdata -> {
                if (qrdata != null) {
                    openPayment(qrdata);
                }
            });
    // The PIN of earlier versions, which the wallet's passphrase moves from to the screen lock.
    private final ActivityResultLauncher<Void> upgradePin =
            registerForActivityResult(new PinActivity.EnterPin(), pin -> {
                if (pin == null) {
                    return;
                }
                try {
                    upgradeFrom(Wallet.SHA256(pin));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
    // The confirmation screen of a payment request, whose result only the send screen needs.
    private final ActivityResultLauncher<Intent> confirmationScreen =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SystemBars.apply(this);
        this.settings = Settings.load(this);
        Network network = settings.getNetwork();
        Networks.showBadge(this, network);
        this.txStore = new TransactionStore(this, network);
        TextView bchBalanceView = findViewById(R.id.bchBalanceView);
        Amount lastBal = new Amount(this.settings.getLastBalance());
        bchBalanceView.setText(getString(R.string.bch_amount, lastBal.toString()));
        // Test coins have no price, so a test network's wallet shows no fiat amounts.
        if (network.hasFiat) {
            this.exchangeRates = ExchangeRates.start();
            new Thread(new ExchangeRateFetcher(this, lastBal)).start();
        }
        createWallet();
        fab = findViewById(R.id.fab);
        fabSettings = findViewById(R.id.btnSettings);
        fabReceive = findViewById(R.id.btnReceive);
        fabSend = findViewById(R.id.btnSend);
        fabScan = findViewById(R.id.btnScan);
        fabQR = findViewById(R.id.btnQR);
        mCLayout = findViewById(R.id.coordinator_layout);
        ChangeTransform changeTransform = new ChangeTransform();
        changeTransform.setDuration(500);
        changeTransform.setInterpolator(new AccelerateInterpolator());
        TransitionManager.beginDelayedTransition(mCLayout, changeTransform);
        List<TransactionData> txs = txStore.getData();
        Collections.sort(txs, Collections.reverseOrder());
        // Without the transactions of the tokens the user hid, which the store keeps.
        mAdapter = new TransactionAdapter(TransactionList.withoutHidden(txs, settings.getHiddenTokens()), this,
                mCLayout, settings.getLastBlockHeight(), network);
        tokenAdapter = new TokenAdapter(this, this::openToken);
        iconLoader = new NftArt(this, (category, commitment) -> redrawTokenIcons());
        mainPages = new MainPages(mAdapter, tokenAdapter, this::refresh, savedInstanceState);
        pages = findViewById(R.id.pages);
        pages.setAdapter(mainPages);
        // The pager keeps its pages in a list of its own, which takes the focus before them. Used
        // without a touch screen, the screen focuses that list before the pages are made, so a
        // whole page showed as focused; taking the focus after them, the list hands it to the
        // first row once they're made, as the screen's list did before it had pages.
        ((ViewGroup) pages.getChildAt(0)).setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        pages.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                showBchPlease();
            }
        });
        tabs = findViewById(R.id.tabs);
        new TabLayoutMediator(tabs, pages, (tab, position) ->
                tab.setText(position == MainPages.TOKENS ? R.string.tab_tokens : R.string.tab_activity)).attach();
        // The tokens the wallet held when it last told them, until it tells them again.
        TokenLabelFile.open(this, network);
        showTokens(settings.getLastTokens());
        showBchPlease();
        fab.setOnClickListener(view -> toggleFABMenu());
        getOnBackPressedDispatcher().addCallback(this, closeMenuOnBack);
        listenForMenuSlides();
        fabQR.setOnClickListener(v -> {
            fab.bringToFront();
            displayQRPopup(true);
            sendViewToBack(v);
        });
        fabScan.setOnClickListener(v -> {
            fab.bringToFront();
            displayQRScanner();
        });
        fabSend.setOnClickListener(v -> {
            fab.bringToFront();
            openSendActivity();
        });
        fabReceive.setOnClickListener(v -> {
            fab.bringToFront();
            openReceiveActivity();
        });
        fabSettings.setOnClickListener(v -> {
            fab.bringToFront();
            openSettingsActivity();
        });
        Intent intent = getIntent();
        boolean launchLoveActivity = intent.getBooleanExtra("launchLoveActivity", false);
        if (launchLoveActivity) {
            // Once, also when the activity is recreated with this intent.
            intent.removeExtra("launchLoveActivity");
            openPayment(SettingsFragment.DONATE_URI);
        }
        if (savedInstanceState != null) {
            boolean qr = savedInstanceState.getBoolean(STATE_QR_POPUP);
            TransactionData details = BundleCompat.getSerializable(savedInstanceState,
                    STATE_TX_DETAILS, TransactionData.class);
            // A popup shows once the screen's window is there.
            mCLayout.post(() -> {
                if (qr) {
                    displayQRPopup(false);
                }
                if (details != null) {
                    mAdapter.showDetails(details);
                }
            });
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_QR_POPUP, qrPopup != null && qrPopup.isShowing());
        outState.putSerializable(STATE_TX_DETAILS, mAdapter.shownDetails());
        mainPages.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        // A popup goes with its window, which a recreated screen replaces.
        if (qrPopup != null) {
            qrPopup.dismiss();
        }
        mAdapter.dismissDetails();
        mAdapter.stop();
        iconLoader.stop();
        pagesHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // The menu button opens the menu while it's held down, and presses the menu's button that it's
    // let go over. A tap goes on as a click, which is also what TalkBack's double tap does, so lint
    // flags it only because FloatingActionButton isn't one of Android's own views.
    @SuppressLint("ClickableViewAccessibility")
    private void listenForMenuSlides() {
        fab.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                toggleFABMenu();
                lastDown = System.currentTimeMillis();
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                toggleFABMenu();
                if (System.currentTimeMillis() - lastDown < 500) {
                    v.performClick();
                } else {
                    if (inViewInBounds(fabQR, (int) event.getRawX(), (int) event.getRawY())) {
                        displayQRPopup(true);
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
        });
    }

    public void createWallet() {
        if (askingForLocalNetwork) {
            return;
        }
        Wallet current = Wallet.getInstance();
        if (current == null) {
            if (!mayStartWallet()) {
                return;
            }
            current = WalletStarter.start(this, settings);
        }
        if (current != wallet) {
            // A wallet this activity doesn't listen to yet: the one it started, one started
            // while the activity was in the background, or the one that kept running while
            // the activity was recreated.
            wallet = current;
            new StartWalletTask(this).execute(current);
        } else if (eventListener != null && current.setMainListener(eventListener)) {
            // Another main screen, opened on top of this one, took the wallet's notifications
            // and is gone: this one takes them back, and catches up on what it missed.
            Wallet attached = current;
            WalletEventListener listener = eventListener;
            new Thread(() -> {
                try {
                    attached.refresh(listener);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, "wallet-refresh").start();
        }
    }

    // mayStartWallet returns whether the wallet can start now. Android 17 and newer let the app
    // reach the local network only with the user's permission, so a wallet set to connect to
    // nodes there asks for it first, once for those nodes, and starts once the user has
    // answered, since a bchd node that it can't reach stops it.
    private boolean mayStartWallet() {
        List<String> local = WalletStarter.localNodesWithoutPermission(this, settings);
        if (local.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN) {
            return true;
        }
        String asked = TextUtils.join(",", local);
        if (!asked.equals(settings.getLocalNetworkAsked())) {
            settings.setLocalNetworkAsked(asked);
            askingForLocalNetwork = true;
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_LOCAL_NETWORK}, RC_LOCAL_NETWORK);
            return false;
        }
        Snackbar snackbar = Snackbar.make(findViewById(R.id.coordinator_layout),
                getString(R.string.local_network_denied, TextUtils.join(", ", local)), Snackbar.LENGTH_LONG);
        snackbar.setTextMaxLines(5);
        snackbar.show();
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RC_LOCAL_NETWORK) {
            askingForLocalNetwork = false;
            createWallet();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (settings != null) {
            // The tokens as the wallet last told them, and the transactions, without a token
            // that its screen hid since, or with one that Settings showed again.
            showTokens(settings.getLastTokens());
            // A token's screen saves what the wallet tells it while it listens, which can be all
            // that's left of a memo once the wallet has started again.
            if (txStore.stale()) {
                txStore = new TransactionStore(this, settings.getNetwork());
            }
            mAdapter.setNewData(TransactionList.withoutHidden(txStore.getData(), settings.getHiddenTokens()));
            showBchPlease();
            redrawLabels();
            mAdapter.retryArt();
            iconLoader.retry();
            // Start the wallet if it stopped while the app was in the background, or listen to
            // the one that WalletCloser started again while another screen was in front.
            createWallet();
        }
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
        scanner.launch(null);
    }

    // displayQRPopup shows the wallet's address and its QR code in a popup, from the menu, which
    // it closes, or again on a recreated screen. Its tabs show the address in its plain form,
    // first, or in its token-aware form, which receives CashTokens and BCH.
    private void displayQRPopup(boolean fromMenu) {
        String lastAddr = "";
        if (!wallet.isRunning()) {
            lastAddr = settings.getLastAddress();
            if (lastAddr.equals("")) {
                Snackbar snackbar = Snackbar.make(mCLayout, R.string.wallet_is_not_loaded_yet, Snackbar.LENGTH_LONG);
                snackbar.show();
                return;
            }
        }
        if (fromMenu) {
            toggleFABMenu();
        }
        LayoutInflater layoutInflater = (LayoutInflater) MainActivity.this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View customView = layoutInflater.inflate(R.layout.qrpopup, mCLayout, false);
        PopupWindow popupWindow = new PopupWindow(customView, CoordinatorLayout.LayoutParams.WRAP_CONTENT, CoordinatorLayout.LayoutParams.WRAP_CONTENT, true);
        popupWindow.showAtLocation(findViewById(R.id.coordinator_layout), Gravity.CENTER, 0, 0);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        qrPopup = popupWindow;
        try {
            String addr;
            String tokenAddr;
            if (wallet.isRunning()) {
                Addresses addresses = wallet.currentAddresses();
                addr = addresses.plain;
                tokenAddr = addresses.tokenAware;
                settings.setLastAddress(addr);
                settings.setLastTokenAddress(tokenAddr);
            } else {
                addr = lastAddr;
                // Empty until the wallet has loaded once since the app started keeping it.
                tokenAddr = settings.getLastTokenAddress();
            }
            qrImage = customView.findViewById(R.id.qrCodeView);
            addrText = customView.findViewById(R.id.address);
            TabLayout forms = customView.findViewById(R.id.addressTabs);
            forms.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    boolean tokenAware = tab.getPosition() == 1;
                    showAddress(customView, tokenAware ? tokenAddr : addr, tokenAware);
                }

                @Override
                public void onTabUnselected(TabLayout.Tab tab) {
                }

                @Override
                public void onTabReselected(TabLayout.Tab tab) {
                }
            });
            showAddress(customView, addr, false);
            // The wallet tells of a payment to either form as one to the plain form.
            wallet.listenAddress(addr, new AddressListener() {
                @Override
                public void onPaymentReceived(long amount) {
                    if (getApplicationContext() != null) {
                        runOnUiThread(() -> {
                            Vibrations.buzz(getApplicationContext());
                            CoinFlip.show(customView.findViewById(R.id.qrCodeLayout));
                        });
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // showAddress shows an address in the QR code's popup, in its plain form or its token-aware
    // one, which tapping it or its code copies.
    private void showAddress(View popup, String address, boolean tokenAware) {
        TextView help = popup.findViewById(R.id.addrHelpText);
        help.setText(tokenAware ? R.string.token_address : R.string.wallet_address);
        if (address.isEmpty()) {
            qrImage.setImageDrawable(null);
            qrImage.setOnClickListener(null);
            addrText.setText(R.string.wallet_is_not_loaded_yet);
            addrText.setOnClickListener(null);
            return;
        }
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        try {
            qrImage.setImageBitmap(QrCodes.encode(wallet.uriPrefix() + address,
                    Math.min(metrics.widthPixels, metrics.heightPixels)));
        } catch (WriterException e) {
            e.printStackTrace();
        }
        qrImage.setOnClickListener(v -> copyToClipboard(address));
        addrText.setText(address);
        addrText.setOnClickListener(v -> copyToClipboard(address));
    }

    private void copyToClipboard(String data) {
        Object clipboardService = getSystemService(CLIPBOARD_SERVICE);
        final ClipboardManager clipboardManager = (ClipboardManager) clipboardService;
        ClipData clipData = ClipData.newPlainText(getString(R.string.source_text), data);
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(clipData);
            Snackbar snackbar = Snackbar.make(mCLayout, R.string.address_copied_to_clipboard, Snackbar.LENGTH_LONG);
            snackbar.show();
        }
    }

    private void toggleFABMenu() {
        toggleRotation(fab);
        if (!isFabOpen) {
            showFABMenu();
        } else {
            closeFABMenu();
        }
    }

    private void showFABMenu() {
        isFabOpen = true;
        closeMenuOnBack.setEnabled(true);
        fabSettings.animate().translationY(-getResources().getDimension(R.dimen.standard_65));
        fabReceive.animate().translationY(-getResources().getDimension(R.dimen.standard_120));
        fabSend.animate().translationY(-getResources().getDimension(R.dimen.standard_175));
        fabScan.animate().translationY(-getResources().getDimension(R.dimen.standard_230));
        fabQR.animate().translationY(-getResources().getDimension(R.dimen.standard_285));
    }

    private void closeFABMenu() {
        isFabOpen = false;
        closeMenuOnBack.setEnabled(false);
        fabSettings.animate().translationY(0);
        fabReceive.animate().translationY(0);
        fabSend.animate().translationY(0);
        fabScan.animate().translationY(0);
        fabQR.animate().translationY(0);
        fab.bringToFront();
    }

    protected void toggleRotation(View v) {
        v.setRotation(isFabOpen ? 0.0f : 45.0f);
    }

    public static void sendViewToBack(final View child) {
        final ViewGroup parent = (ViewGroup) child.getParent();
        if (null != parent) {
            parent.removeView(child);
            parent.addView(child, 0);
        }
    }

    // openPayment opens a payment link: the confirmation screen of its payment request, or the
    // send screen, filled in from the link or address, unless the wallet can't pay it as it asks.
    private void openPayment(String qrdata) {
        BitcoinPaymentURI uri = BitcoinPaymentURI.parse(qrdata);
        if (uri != null && PaymentLinks.refuse(mCLayout, uri)) {
            return;
        }
        if (uri != null && uri.getR() != null) {
            PaymentRequests.pay(this, mCLayout, wallet, qrdata, settings.getFiatCurrency(), settings.getFeePerByte(),
                    confirmationScreen);
        } else {
            Intent intent = new Intent(this, SendActivity.class);
            intent.putExtra("fiatCurrency", this.settings.getFiatCurrency());
            intent.putExtra("feePerByte", this.settings.getFeePerByte());
            intent.putExtra("qrdata", qrdata);
            startActivity(intent);
        }
    }

    // upgradeWalletPassphrase replaces the private passphrase of earlier versions with a random one
    // sealed with WalletSecret: right away for the default passphrase, and once the user enters
    // the PIN or fingerprint that unlocks it. A PIN is replaced only when the phone has a screen
    // lock to ask for instead.
    void upgradeWalletPassphrase() {
        switch (settings.getEncryptionType()) {
            case UNENCRYPTED:
                PassphraseUpgrade.run(Wallet.getInstance(), settings, Wallet.DEFAULT_PASSPHRASE,
                        ContextCompat.getMainExecutor(this), new PassphraseUpgrade.Callback() {
                            @Override
                            public void onUpgraded(String passphrase) {
                            }

                            @Override
                            public void onFailed(Throwable t) {
                                t.printStackTrace();
                            }
                        });
                break;
            case PIN:
                if (ScreenLock.isSet(this)) {
                    askToUpgrade(R.string.upgrade_from_pin, () -> {
                        if (settings.pinLockedOut()) {
                            Snackbar.make(mCLayout, R.string.pin_locked_out, Snackbar.LENGTH_LONG).show();
                            return;
                        }
                        upgradePin.launch(null);
                    });
                }
                break;
            case FINGERPRINT:
                askToUpgrade(R.string.upgrade_from_fingerprint, () ->
                        LegacyFingerprint.read(this, settings, new LegacyFingerprint.Callback() {
                            @Override
                            public void onPassphrase(String passphrase) {
                                upgradeFrom(passphrase);
                            }

                            @Override
                            public void onFailed(CharSequence message) {
                                Snackbar snackbar = Snackbar.make(mCLayout, message, Snackbar.LENGTH_LONG);
                                snackbar.setTextMaxLines(5);
                                snackbar.show();
                            }
                        }));
                break;
            default:
                break;
        }
    }

    private void askToUpgrade(int message, Runnable upgrade) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.upgrade_title)
                .setMessage(message)
                .setPositiveButton(R.string.upgrade_switch, (dialog, which) -> upgrade.run())
                .setNegativeButton(R.string.upgrade_later, null)
                .show();
    }

    private void upgradeFrom(String oldPassphrase) {
        PassphraseUpgrade.run(Wallet.getInstance(), settings, oldPassphrase, ContextCompat.getMainExecutor(this),
                new PassphraseUpgrade.Callback() {
                    @Override
                    public void onUpgraded(String passphrase) {
                        Snackbar.make(mCLayout, R.string.upgrade_done, Snackbar.LENGTH_LONG).show();
                    }

                    @Override
                    public void onFailed(Throwable t) {
                        String message;
                        if (Wallet.isWrongPassphrase(t) && settings.getEncryptionType() == EncryptionType.PIN) {
                            settings.countInvalidPin();
                            message = getString(R.string.upgrade_wrong_pin);
                        } else {
                            String reason = Wallet.reasonFor(t);
                            message = getString(R.string.upgrade_failed, reason != null ? reason : t.toString());
                        }
                        Snackbar snackbar = Snackbar.make(mCLayout, message, Snackbar.LENGTH_LONG);
                        snackbar.setTextMaxLines(5);
                        snackbar.show();
                    }
                });
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();
        maybeSendDonationReminder();
        if (exchangeRates != null) {
            try {
                TextView fiatBalanceView = findViewById(R.id.fiatBalanceView);
                Amount amt = new Amount(settings.getLastBalance());
                String formatted = exchangeRates.getFormattedAmountInFiat(amt, Currency.getInstance(settings.getFiatCurrency()));
                fiatBalanceView.setText(formatted);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (Wallet.getInstance() != null && Wallet.getInstance().isRunning()) {
            openLaunchPayment();
        }
        startSyncStatusPoller();
    }

    // openLaunchPayment opens the payment link or payment request file that started the app,
    // and removes it from the intent itself, of which getExtras returns a copy, so that it opens
    // once rather than every time this screen comes back.
    private void openLaunchPayment() {
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        String uri = intent.getStringExtra("uri");
        if (uri != null && !uri.equals("")) {
            intent.removeExtra("uri");
            openPayment(uri);
        }
        byte[] paymentRequest = intent.getByteArrayExtra(PaymentRequestFile.EXTRA);
        if (paymentRequest != null) {
            intent.removeExtra(PaymentRequestFile.EXTRA);
            PaymentRequests.payFile(this, mCLayout, wallet, paymentRequest, settings.getFiatCurrency(),
                    settings.getFeePerByte(), confirmationScreen);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        syncStatusPoller.shutdownNow();
    }

    // startSyncStatusPoller shows how far the wallet is from following the network, from its
    // Network call every two seconds until the activity pauses.
    private void startSyncStatusPoller() {
        syncStatusPoller = Executors.newSingleThreadScheduledExecutor();
        syncStatusPoller.scheduleWithFixedDelay(() -> {
            SyncStatus status = null;
            Wallet wallet = Wallet.getInstance();
            if (wallet != null && wallet.isRunning()) {
                try {
                    status = SyncStatus.of(wallet.network());
                } catch (Exception e) {
                    // The wallet is starting or stopping, so there is nothing to show yet.
                }
            }
            SyncStatus shown = status;
            runOnUiThread(() -> showSyncStatus(shown));
        }, 0, SYNC_STATUS_POLL_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void showSyncStatus(SyncStatus status) {
        TextView syncStatusView = findViewById(R.id.syncStatusView);
        if (status == null || status.state == SyncStatus.State.SYNCED) {
            syncStatusView.setVisibility(View.GONE);
            return;
        }
        NumberFormat heights = NumberFormat.getIntegerInstance();
        switch (status.state) {
            case WAITING_FOR_PEERS:
                syncStatusView.setText(R.string.sync_waiting_for_peers);
                break;
            case SYNCING:
                if (status.height < status.target) {
                    syncStatusView.setText(getString(R.string.sync_syncing, heights.format(status.height), heights.format(status.target)));
                } else {
                    syncStatusView.setText(R.string.sync_syncing_at_tip);
                }
                break;
            case UPDATING_WALLET:
                syncStatusView.setText(getString(R.string.sync_updating_wallet, heights.format(status.height), heights.format(status.target)));
                break;
        }
        syncStatusView.setVisibility(View.VISIBLE);
    }

    // askTokenLabels asks the wallet what it can say of the tokens that show, which is also what
    // makes them load, so a hidden token costs nothing. With the setting on, it also loads the
    // icons it can.
    private void askTokenLabels(List<TokenBalance> shown) {
        showTokenIcons();
        Wallet wallet = Wallet.getInstance();
        if (wallet == null || !wallet.isRunning()) {
            return;
        }
        List<String> categories = new ArrayList<>();
        for (TokenBalance token : shown) {
            categories.add(token.category);
        }
        runOnUiThread(() -> peekTokenLabels(wallet, categories));

        // In parts, so that a reply stays well under what gRPC takes.
        for (List<String> part : Lists.partition(categories, TOKEN_LABELS_PER_CALL)) {
            Futures.addCallback(wallet.tokenMetadataAsync(part, false), new FutureCallback<List<TokenMeta>>() {
                @Override
                public void onSuccess(List<TokenMeta> tokens) {
                    TokenLabels.update(tokens);
                    runOnUiThread(MainActivity.this::redrawLabels);
                    if (settings.getTokenImages()) {
                        loadTokenIcons(tokens);
                    }
                    if (searching(tokens)) {
                        askTokenLabelsSoon();
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    t.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    // peekTokenLabels asks what the wallet already knows of the tokens in the transactions that it
    // doesn't hold any more, so that their rows read right, without having them load.
    private void peekTokenLabels(Wallet wallet, List<String> held) {
        Set<String> hidden = settings.getHiddenTokens();
        Set<String> unknown = new HashSet<>();
        for (TransactionData tx : mAdapter.getData()) {
            for (TokenChange change : tx.getTokens()) {
                if (!TokenLabels.saidThisRun(change.category) && !hidden.contains(change.category)) {
                    unknown.add(change.category);
                }
            }
        }
        unknown.removeAll(held);
        for (List<String> part : Lists.partition(new ArrayList<>(unknown), TOKEN_LABELS_PER_CALL)) {
            Futures.addCallback(wallet.peekTokenMetadataAsync(part, false), new FutureCallback<List<TokenMeta>>() {
                @Override
                public void onSuccess(List<TokenMeta> tokens) {
                    TokenLabels.update(tokens);
                    runOnUiThread(MainActivity.this::redrawLabels);
                }

                @Override
                public void onFailure(Throwable t) {
                    t.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    // searching returns whether the wallet is still looking for any of the tokens.
    private static boolean searching(List<TokenMeta> tokens) {
        for (TokenMeta token : tokens) {
            if (token.loadsByItself && token.status == TokenMeta.Status.SEARCHING) {
                return true;
            }
        }
        return false;
    }

    // askTokenLabelsSoon asks again in a while, as the wallet tells nothing by itself.
    private void askTokenLabelsSoon() {
        if (labelsAskPending.compareAndSet(false, true)) {
            pagesHandler.postDelayed(() -> {
                labelsAskPending.set(false);
                showTokens(settings.getLastTokens());
            }, TOKEN_LABELS_INTERVAL_MS);
        }
    }

    // redrawLabels redraws the rows if the labels changed since they were drawn, on any screen.
    private void redrawLabels() {
        long version = TokenLabels.version();
        if (version != drawnLabels) {
            drawnLabels = version;
            tokenAdapter.labelsChanged();
            mAdapter.labelsChanged();
        }
    }

    // showTokenIcons says which icons to show, and redraws the rows if that changed, as when the
    // user loaded one from a token's screen.
    private void showTokenIcons() {
        boolean all = settings.getTokenImages();
        Set<String> loaded = settings.getLoadedIcons();
        TokenIcons.configure(all, loaded);
        String shown = all + " " + new TreeSet<>(loaded) + " " + TokenIcons.version();
        if (!shown.equals(shownTokenIcons)) {
            shownTokenIcons = shown;
            runOnUiThread(this::redrawTokenIcons);
        }
    }

    private void redrawTokenIcons() {
        tokenAdapter.iconsChanged();
        mAdapter.iconsChanged();
    }

    // loadTokenIcons loads the icons of the tokens that don't have theirs yet.
    private void loadTokenIcons(List<TokenMeta> tokens) {
        for (TokenMeta token : tokens) {
            if (!token.iconUri.isEmpty() && !token.lookAlike) {
                runOnUiThread(() -> iconLoader.requestIcon(token.category, token.iconUri));
            }
        }
    }

    // showTokens lists the wallet's tokens on the Tokens page, but those the user hid, and shows
    // the tabs while it holds any others. The header keeps its height: the tabs take its bottom,
    // and the fiat balance's line gets shorter so that the sync status fits above them.
    void showTokens(List<TokenBalance> tokens) {
        Set<String> hidden = settings.getHiddenTokens();
        List<TokenBalance> shown = new ArrayList<>();
        for (TokenBalance token : tokens) {
            if (!hidden.contains(token.category)) {
                shown.add(token);
            }
        }
        tokenAdapter.setTokens(shown);
        askTokenLabels(shown);
        boolean any = !shown.isEmpty();
        tabs.setVisibility(any ? View.VISIBLE : View.GONE);
        pages.setUserInputEnabled(any);
        if (!any && pages.getCurrentItem() != MainPages.ACTIVITY) {
            pages.setCurrentItem(MainPages.ACTIVITY, false);
        }
        TextView fiatBalanceView = findViewById(R.id.fiatBalanceView);
        ViewGroup.LayoutParams lp = fiatBalanceView.getLayoutParams();
        int height = any ? ViewGroup.LayoutParams.WRAP_CONTENT
                : getResources().getDimensionPixelSize(R.dimen.fiat_balance_height);
        if (lp.height != height) {
            lp.height = height;
            fiatBalanceView.setLayoutParams(lp);
        }
    }

    // openToken opens the screen of a token on the Tokens page, or of its NFTs.
    private void openToken(TokenBalance token) {
        TokenActivity.open(this, token);
    }

    // showBchPlease asks for BCH on the Activity page while the wallet has no transactions.
    private void showBchPlease() {
        boolean empty = mAdapter.getItemCount() == 0;
        boolean shown = empty && pages.getCurrentItem() == MainPages.ACTIVITY;
        findViewById(R.id.bchPlease).setVisibility(shown ? View.VISIBLE : View.GONE);
    }

    // refresh asks the wallet again, off the main thread, for pull to refresh on either page, and
    // for its balance and tokens too, which a notification the screen missed would have left
    // behind.
    private void refresh() {
        Wallet current = wallet;
        WalletEventListener listener = eventListener;
        if (current == null || listener == null) {
            mainPages.stopRefreshing();
            return;
        }
        new Thread(() -> {
            try {
                current.refresh(listener);
            } catch (Exception e) {
                e.printStackTrace();
            }
            runOnUiThread(mainPages::stopRefreshing);
        }, "wallet-refresh").start();
    }

    private static class ExchangeRateFetcher implements Runnable {
        private final WeakReference<MainActivity> mainActivityRef;
        private final Amount lastBal;

        ExchangeRateFetcher(MainActivity mainActivity, Amount lastBal) {
            mainActivityRef = new WeakReference<>(mainActivity);
            this.lastBal = lastBal;
        }

        @Override
        public void run() {
            MainActivity mainActivity = mainActivityRef.get();
            if (mainActivity == null) {
                return;
            }
            String fiatCurrency = mainActivity.settings.getFiatCurrency();
            try {
                mainActivity.exchangeRates.fetchFormattedAmountInFiat(lastBal, Currency.getInstance(fiatCurrency), new ExchangeRates.Callback() {
                    @Override
                    public void onRateFetched(String formatted) {
                        MainActivity mainActivity2 = mainActivityRef.get();
                        if (mainActivity2 == null) {
                            return;
                        }
                        TextView fiatBalanceView = mainActivity2.findViewById(R.id.fiatBalanceView);
                        fiatBalanceView.setText(formatted);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void maybeSendBackupReminder() {
        if (!settings.getBackupReminder() && !settings.getMnemonic().equals("")) {
            Intent intent = new Intent(getApplicationContext(), BackupActivity.class);
            postNotification(5678, intent, getString(R.string.backup_your_wallet),
                    getString(R.string.backup_recovery_phrase_suggestion));
            settings.setBackupReminder(true);
        }
    }

    // Remind every 45 days that the wallet is free software by volunteers
    // who accept donations. This used to be posted by a background service.
    private void maybeSendDonationReminder() {
        // The donation address is a mainnet one.
        if (settings.getNetwork() != Network.MAINNET) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = settings.getLastNotification();
        if (last == 0) {
            settings.setLastNotification(now);
            return;
        }
        if (last + DONATION_REMINDER_INTERVAL > now) {
            return;
        }
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.putExtra("launchLoveActivity", true);
        postNotification(1234, intent, getString(R.string.donation_reminder_title),
                getString(R.string.donation_reminder_text));
        settings.setLastNotification(now);
    }

    private void postNotification(int id, Intent intent, String title, String text) {
        // Ask once for the permission to post notifications, which Android 13
        // and newer require.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && !settings.getNotificationsRequested()) {
            settings.setNotificationsRequested(true);
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2);
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), id, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new NotificationCompat.Builder(getApplicationContext(), "default")
                .setSmallIcon(R.drawable.neutrino_small)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentTitle(title)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_SOUND|Notification.DEFAULT_LIGHTS|Notification.DEFAULT_VIBRATE)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .build();

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel("default", "default", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("default channel");
        notificationManager.createNotificationChannel(channel);
        notificationManager.notify(id, notification);
    }

    // StartWalletTask loads the started wallet and shows its balance and transactions. The
    // wallet calls the listener on background threads, so the listener updates the views on
    // the UI thread.
    private static class StartWalletTask {
        private final WeakReference<MainActivity> mainActivityRef;

        StartWalletTask(MainActivity mainActivity) {
            mainActivityRef = new WeakReference<>(mainActivity);
        }

        void execute(Wallet wallet) {
            try {
                WalletEventListener listener = new WalletEventListener() {
                    @Override
                    public void onWalletReady() {
                        MainActivity mainActivity = mainActivityRef.get();
                        if (mainActivity == null) {
                            // no can do if we no longer have a reference to MainActivity.
                            // perhaps print a warning.
                            return;
                        }
                        mainActivity.runOnUiThread(() -> {
                            mainActivity.openLaunchPayment();
                            mainActivity.upgradeWalletPassphrase();
                        });
                    }

                    @Override
                    public void onWalletError(String message) {
                        MainActivity mainActivity = mainActivityRef.get();
                        if (mainActivity == null) {
                            return;
                        }
                        mainActivity.runOnUiThread(() -> {
                            Snackbar snackbar = Snackbar.make(mainActivity.mCLayout,
                                    mainActivity.getString(R.string.wallet_error, message), Snackbar.LENGTH_INDEFINITE);
                            snackbar.show();
                        });
                    }

                    @Override
                    public void onBalanceChange(long bal) {
                        MainActivity mainActivity = mainActivityRef.get();
                        if (mainActivity == null) {
                            // no can do if we no longer have a reference to MainActivity.
                            // perhaps print a warning.
                            return;
                        }
                        if (mainActivity.getApplicationContext() != null) {
                            mainActivity.settings.setLastBalance(bal);
                            mainActivity.runOnUiThread(() -> {
                                Amount amt = new Amount(bal);
                                TextView bchBalanceView = mainActivity.findViewById(R.id.bchBalanceView);
                                bchBalanceView.setText(mainActivity.getString(R.string.bch_amount, amt.toString()));
                                if (mainActivity.exchangeRates != null) {
                                    try {
                                        String fiatAmount = mainActivity.exchangeRates.getFormattedAmountInFiat(amt, Currency.getInstance(mainActivity.settings.getFiatCurrency()));
                                        TextView fiatBalanceView = mainActivity.findViewById(R.id.fiatBalanceView);
                                        fiatBalanceView.setText(fiatAmount);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                                if (bal > 0) {
                                    mainActivity.maybeSendBackupReminder();
                                }
                            });
                        }
                    }

                    @Override
                    public void onTokenBalances(List<TokenBalance> tokens) {
                        MainActivity mainActivity = mainActivityRef.get();
                        if (mainActivity == null) {
                            return;
                        }
                        mainActivity.settings.setLastTokens(tokens);
                        mainActivity.runOnUiThread(() -> mainActivity.showTokens(tokens));
                    }

                    @Override
                    public void onWalletCreated(String seed) {
                        MainActivity mainActivity = mainActivityRef.get();
                        if (mainActivity == null) {
                            return;
                        }
                        mainActivity.settings.setWalletInitialized(true);
                        mainActivity.settings.setMnemonic(seed);
                    }

                    @Override
                    public void onGetTransactions(List<TransactionData> txs, int blockHeight) {
                        MainActivity mainActivity = mainActivityRef.get();
                        if (mainActivity == null) {
                            return;
                        }
                        // This isn't the UI thread, and the file can be large: a rescan's replies
                        // would each hold up the screen.
                        String fiatCurrency = mainActivity.settings.getFiatCurrency();
                        for (TransactionData tx : txs) {
                            tx.setFiatCurrency(fiatCurrency);
                            String formattedFiat = "";
                            if (mainActivity.exchangeRates != null) {
                                try {
                                    formattedFiat = mainActivity.exchangeRates.getFormattedAmountInFiat(new Amount(tx.shownAmount()), Currency.getInstance(fiatCurrency));
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                            tx.setFiatAmount(formattedFiat);
                        }
                        // The wallet's transactions, which drops the ones it no longer has, such as
                        // one a double spend replaced. The store keeps all of them, and the list
                        // leaves out those of hidden tokens.
                        TransactionStore store = TransactionStore.merge(mainActivity.getApplicationContext(),
                                mainActivity.settings.getNetwork(), txs);
                        mainActivity.runOnUiThread(() -> {
                            MainActivity mainActivity2 = mainActivityRef.get();
                            if (mainActivity2 == null) {
                                return;
                            }
                            mainActivity2.txStore = store;
                            // A transaction added at the top goes above the first row shown, so
                            // the list scrolls to show it when it showed the newest before.
                            LinearLayoutManager rows = mainActivity2.mainPages.rows(MainPages.ACTIVITY);
                            boolean atTop = rows == null || rows.findFirstVisibleItemPosition() <= 0;
                            mainActivity2.mAdapter.setNewData(TransactionList.withoutHidden(store.getData(),
                                    mainActivity2.settings.getHiddenTokens()));
                            mainActivity2.showTokens(mainActivity2.settings.getLastTokens());
                            if (atTop && rows != null) {
                                rows.scrollToPosition(0);
                            }
                            mainActivity2.showBchPlease();
                        });
                    }

                    @Override
                    public void onBlock(int blockHeight, String blockHash) {
                        MainActivity mainActivity = mainActivityRef.get();
                        if (mainActivity == null) {
                            return;
                        }
                        if (mainActivity.getApplicationContext() != null) {
                            mainActivity.runOnUiThread(() -> {
                                MainActivity mainActivity2 = mainActivityRef.get();
                                if (mainActivity2 == null) {
                                    return;
                                }
                                mainActivity2.mAdapter.setBlockHeight(blockHeight);
                                mainActivity2.showTokens(mainActivity2.settings.getLastTokens());
                                mainActivity2.settings.setLastBlockHeight(blockHeight);
                                mainActivity2.settings.setLastBlockHash(blockHash);
                            });
                        }
                    }

                    @Override
                    public void onScanProgress(int height) {
                        MainActivity mainActivity = mainActivityRef.get();
                        mainActivity.settings.setScanHeight(height);
                    }
                };
                MainActivity mainActivity = mainActivityRef.get();
                if (mainActivity == null) {
                    return;
                }
                mainActivity.eventListener = listener;
                wallet.loadWallet(listener, mainActivity.settings.getMnemonic());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
