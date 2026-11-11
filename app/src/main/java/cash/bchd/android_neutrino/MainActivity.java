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
import android.widget.ProgressBar;
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
import com.google.common.util.concurrent.ListenableFuture;

import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
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
import cash.bchd.android_neutrino.wallet.TokenLabelFile;
import cash.bchd.android_neutrino.wallet.TokenMeta;
import cash.bchd.android_neutrino.wallet.TransactionData;
import cash.bchd.android_neutrino.wallet.TransactionList;
import cash.bchd.android_neutrino.wallet.Wallet;
import cash.bchd.android_neutrino.wallet.WalletEventListener;
import walletrpc.Api;



public class MainActivity extends CloseActivity {

    // EXTRA_HIDDEN_TOKEN is the token a screen just hid, whose hiding can be undone.
    static final String EXTRA_HIDDEN_TOKEN = "hiddenToken";
    // Reads the saved transactions, one read at a time.
    private static final ExecutorService STORE_READER = Executors.newSingleThreadExecutor();
    // How long the hiding of a token can be undone.
    private static final int UNDO_HIDE_MS = 5000;

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
    // Both forms of the address the popup shows, once the wallet gave them, and the request for
    // them, which closing the popup cancels.
    private Addresses popupAddresses;
    private ListenableFuture<Addresses> addressRequest;
    // Stops the address popup's listening for payments to its address, or null.
    private Runnable listening;
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
    // Whether the rows are to be redrawn for the icons that loaded, at the next frame.
    private boolean iconsRedrawPending;
    // The tokens to ask about again, as the wallet was still looking for them.
    private final Set<String> labelsAsking = new HashSet<>();
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
    private MainListener eventListener;
    // Redraws the labels once those kept from the last run are loaded.
    private final Runnable redrawOnLoad = () -> runOnUiThread(this::redrawLabels);
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
        // Read off the main thread, which onStart has done, as it's stale.
        this.txStore = TransactionStore.empty(network);
        TextView bchBalanceView = findViewById(R.id.bchBalanceView);
        Amount lastBal = new Amount(this.settings.getLastBalance());
        bchBalanceView.setText(getString(R.string.bch_amount, lastBal.toString()));
        // Test coins have no price, so a test network's wallet shows no fiat amounts.
        if (network.hasFiat) {
            this.exchangeRates = ExchangeRates.start();
            fetchRates();
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
        mAdapter = new TransactionAdapter(new ArrayList<>(), this, mCLayout, settings.getLastBlockHeight(), network);
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
        // The rows are yet to draw, so they draw with these.
        drawnLabels = TokenLabels.version();
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
        TokenLabels.stopOnLoad(redrawOnLoad);
        MainListener.hide(this);
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
            // while the activity was in the background, or the one that kept running or loading
            // while the activity was recreated, which tells the listener its load began with.
            wallet = current;
            eventListener = MainListener.of(current, this);
            MainListener.show(this);
            if (current.loadWallet(eventListener, settings::getMnemonic)) {
                return;
            }
        }
        attach(current);
    }

    // attach has the wallet, loaded or loading, tell this screen's listener once it runs, in place
    // of a token's screen's or the one of its start after a stop, and catch it up on what those
    // were told meanwhile. The listener had the notifications otherwise, and kept what they told.
    private void attach(Wallet current) {
        MainListener listener = eventListener;
        Futures.addCallback(Wallet.whenRunning(), new FutureCallback<Wallet>() {
            @Override
            public void onSuccess(Wallet running) {
                if (running != current || !running.setMainListener(listener)) {
                    return;
                }
                new Thread(() -> {
                    try {
                        running.refresh(listener);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }, "wallet-refresh").start();
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
            }
        }, MoreExecutors.directExecutor());
    }

    // walletReady runs what follows the wallet's load, once a load, on the main screen that shows.
    private void walletReady() {
        openLaunchPayment();
        upgradeWalletPassphrase();
        forgetHiddenTokens();
    }

    // showBalance shows the balance, and what it's worth if there is a rate.
    private void showBalance(long bal) {
        Amount amt = new Amount(bal);
        TextView bchBalanceView = findViewById(R.id.bchBalanceView);
        bchBalanceView.setText(getString(R.string.bch_amount, amt.toString()));
        if (settings.getNetwork().hasFiat) {
            String fiatAmount = ExchangeRates.formattedOrEmpty(amt, Currency.getInstance(settings.getFiatCurrency()));
            if (!fiatAmount.isEmpty()) {
                TextView fiatBalanceView = findViewById(R.id.fiatBalanceView);
                fiatBalanceView.setText(fiatAmount);
            }
        }
    }

    // showStore shows the transactions of the store the wallet's were saved to.
    private void showStore(TransactionStore store) {
        txStore = store;
        // A transaction added at the top goes above the first row shown, so the list scrolls to
        // show it when it showed the newest before. With nothing added it stays put, as the user
        // can be scrolling.
        LinearLayoutManager rows = mainPages.rows(MainPages.ACTIVITY);
        boolean atTop = rows == null || rows.findFirstVisibleItemPosition() <= 0;
        List<TransactionData> shown = mAdapter.getData();
        List<TransactionData> list = TransactionList.withoutHidden(store.getData(), settings.getHiddenTokens());
        boolean newTop = TransactionRows.newTop(shown, list);
        mAdapter.setNewData(list);
        showTokens(settings.getLastTokens());
        if (atTop && newTop && rows != null) {
            rows.scrollToPosition(0);
        }
        showBchPlease();
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
            // The labels kept from the last run can come after the screen shows.
            TokenLabels.onLoad(redrawOnLoad);
            TokenLabels.hold(settings.getHiddenTokens());
            // The tokens as the wallet last told them, and the transactions, without a token
            // that its screen hid since, or with one that Settings showed again.
            showTokens(settings.getLastTokens());
            // A token's screen saves what the wallet tells it while it listens, which can be all
            // that's left of a memo once the wallet has started again.
            if (txStore.stale()) {
                readStore();
            }
            mAdapter.setNewData(TransactionList.withoutHidden(txStore.getData(), settings.getHiddenTokens()));
            showBchPlease();
            redrawLabels();
            mAdapter.retryArt();
            iconLoader.retry();
            // Start the wallet if it stopped while the app was in the background, or listen to
            // the one that WalletCloser started again while another screen was in front.
            createWallet();
            // What the wallet told while another main screen showed, which the listener kept.
            MainListener.show(this);
            showBalance(settings.getLastBalance());
            mAdapter.setBlockHeight(settings.getLastBlockHeight());
            offerUndoHide();
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

    // As long as an address, which the popup's text keeps the room of, unseen, while the wallet is
    // asked for the address, so that the popup doesn't grow when it comes.
    static final String ADDRESS_SIZED = "qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq";

    // displayQRPopup shows the wallet's address and its QR code in a popup, from the menu, which
    // it closes, or again on a recreated screen. Its tabs show the address in its plain form,
    // first, or in its token-aware form, which receives CashTokens and BCH. The address is always
    // the wallet's first unused one, asked for off the main thread, as a busy wallet answers late,
    // with a spinner until it comes, and never one kept from before, which could have been paid.
    private void displayQRPopup(boolean fromMenu) {
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
        popupWindow.setOnDismissListener(() -> {
            if (addressRequest != null) {
                addressRequest.cancel(true);
            }
            stopListening();
        });
        qrPopup = popupWindow;
        qrImage = customView.findViewById(R.id.qrCodeView);
        addrText = customView.findViewById(R.id.address);
        TabLayout forms = customView.findViewById(R.id.addressTabs);
        forms.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showAddress(customView, tab.getPosition() == 1);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
        askAddresses(customView, popupWindow, forms);
    }

    // stopListening stops the address popup's listening for payments, as its listener holds the
    // screen, and a payment to the address once it closed is no news to celebrate.
    private void stopListening() {
        if (listening != null) {
            listening.run();
            listening = null;
        }
    }

    // askAddresses asks the wallet for both forms of its address, once one runs, and shows them in
    // the popup when they come, or says that it couldn't get them, which a tap asks again.
    private void askAddresses(View popup, PopupWindow window, TabLayout forms) {
        popupAddresses = null;
        ListenableFuture<Addresses> request = Wallet.currentAddressesAsync();
        addressRequest = request;
        showAddress(popup, forms.getSelectedTabPosition() == 1);
        Futures.addCallback(request, new FutureCallback<Addresses>() {
            @Override
            public void onSuccess(Addresses addresses) {
                if (!window.isShowing() || request != addressRequest) {
                    return;
                }
                popupAddresses = addresses;
                showAddress(popup, forms.getSelectedTabPosition() == 1);
                // The wallet tells of a payment to either form as one to the plain form.
                Wallet wallet = Wallet.getInstance();
                if (wallet == null) {
                    return;
                }
                stopListening();
                AddressListener listener = new AddressListener() {
                    @Override
                    public void onPaymentReceived(long amount) {
                        runOnUiThread(() -> {
                            Vibrations.buzz(getApplicationContext());
                            CoinFlip.show(popup.findViewById(R.id.qrCodeLayout));
                        });
                    }
                };
                wallet.listenAddress(addresses.plain, listener);
                listening = () -> wallet.stopListening(addresses.plain, listener);
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                if (t instanceof CancellationException || !window.isShowing() || request != addressRequest) {
                    return;
                }
                t.printStackTrace();
                popup.findViewById(R.id.qrCodeLoading).setVisibility(View.GONE);
                addrText.setTextColor(Color.BLACK);
                addrText.setText(R.string.address_unavailable);
                View.OnClickListener again = v -> askAddresses(popup, window, forms);
                addrText.setOnClickListener(again);
                qrImage.setOnClickListener(again);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // showAddress shows the popup's address in its plain form or its token-aware one, which tapping
    // it or its code copies, or the spinner while the wallet is asked for it.
    private void showAddress(View popup, boolean tokenAware) {
        TextView help = popup.findViewById(R.id.addrHelpText);
        help.setText(tokenAware ? R.string.token_address : R.string.wallet_address);
        ProgressBar loading = popup.findViewById(R.id.qrCodeLoading);
        Addresses addresses = popupAddresses;
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int size = Math.min(metrics.widthPixels, metrics.heightPixels);
        if (addresses == null) {
            loading.setVisibility(View.VISIBLE);
            qrImage.setImageDrawable(QrCodes.blank(size));
            qrImage.setOnClickListener(null);
            addrText.setText(ADDRESS_SIZED);
            addrText.setTextColor(Color.TRANSPARENT);
            addrText.setOnClickListener(null);
            return;
        }
        String address = tokenAware ? addresses.tokenAware : addresses.plain;
        loading.setVisibility(View.GONE);
        try {
            qrImage.setImageBitmap(QrCodes.encode(settings.getNetwork().uriPrefix() + address, size));
        } catch (WriterException e) {
            e.printStackTrace();
        }
        qrImage.setOnClickListener(v -> copyToClipboard(address));
        addrText.setTextColor(Color.BLACK);
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

    // readStore reads the saved transactions off the main thread, as a wallet with many takes a
    // while to, and shows them, unless a newer store came in the meantime, as from the wallet.
    private void readStore() {
        Context app = getApplicationContext();
        Network network = settings.getNetwork();
        WeakReference<MainActivity> ref = new WeakReference<>(this);
        STORE_READER.execute(() -> {
            TransactionStore store = new TransactionStore(app, network);
            Collections.sort(store.getData(), Collections.reverseOrder());
            // Through the reference, as the screen isn't to be held while the store is read.
            MainActivity activity = ref.get();
            if (activity == null) {
                return;
            }
            activity.runOnUiThread(() -> {
                if (activity.isDestroyed() || activity.txStore.newerThan(store)
                        || activity.settings.getNetwork() != network) {
                    return;
                }
                activity.txStore = store;
                // Without the transactions of the tokens the user hid, which the store keeps.
                activity.mAdapter.setNewData(TransactionList.withoutHidden(store.getData(),
                        activity.settings.getHiddenTokens()));
                activity.showBchPlease();
            });
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // A token's screen comes back here when it hides the token. A screen that was stopped
        // starts before it's given the new intent, so this offers the Undo, and onStart offers it
        // for a screen that's made anew. Whichever comes first takes it.
        setIntent(intent);
        offerUndoHide();
    }

    // The token whose hiding can still be undone, which isn't to be forgotten yet.
    private String undoable;

    // offerUndoHide says that a token's screen hid it, and offers to show it again for a few
    // seconds, once. Then what's kept of it is forgotten.
    private void offerUndoHide() {
        String category = getIntent().getStringExtra(EXTRA_HIDDEN_TOKEN);
        if (category == null) {
            return;
        }
        getIntent().removeExtra(EXTRA_HIDDEN_TOKEN);
        undoable = category;
        Snackbar.make(mCLayout, R.string.token_hidden, UNDO_HIDE_MS).setAction(R.string.undo, v -> {
            settings.showToken(category);
            showTokens(settings.getLastTokens());
            mAdapter.setNewData(TransactionList.withoutHidden(txStore.getData(), settings.getHiddenTokens()));
        }).addCallback(new Snackbar.Callback() {
            @Override
            public void onDismissed(Snackbar snackbar, int event) {
                if (category.equals(undoable)) {
                    undoable = null;
                }
                if (event != DISMISS_EVENT_ACTION && settings.getHiddenTokens().contains(category)) {
                    forgetTokens(Collections.singletonList(category));
                }
            }
        }).show();
    }

    // forgetTokens drops all that the wallet and the app keep of hidden tokens, a collection's NFT
    // types and art can be large, but their labels, so that Hidden Tokens can name them. Shown
    // again, one is found as if new. Until the wallet has, it's tried again each time the wallet
    // starts. Their images go off the main thread, all of them in one pass over their folders.
    private void forgetTokens(List<String> categories) {
        if (categories.isEmpty()) {
            return;
        }
        TokenIcons.forgetLater(this, categories);
        Wallet wallet = Wallet.getInstance();
        boolean running = wallet != null && wallet.isRunning();
        for (String category : categories) {
            settings.removeLoadedIcon(category);
            if (!running) {
                continue;
            }
            Futures.addCallback(wallet.forgetTokenAsync(category), new FutureCallback<Api.ForgetTokenResponse>() {
                @Override
                public void onSuccess(Api.ForgetTokenResponse reply) {
                    // On the main thread, as hiding and showing change the same list there.
                    runOnUiThread(() -> settings.forgotToken(category));
                }

                @Override
                public void onFailure(Throwable t) {
                    t.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    // forgetHiddenTokens forgets the hidden tokens that weren't forgotten when they were hidden,
    // but one whose hiding can still be undone.
    private void forgetHiddenTokens() {
        Set<String> hidden = settings.getHiddenTokens();
        List<String> forget = new ArrayList<>();
        for (String category : settings.getTokensToForget()) {
            if (!hidden.contains(category)) {
                settings.forgotToken(category);
            } else if (!category.equals(undoable)) {
                forget.add(category);
            }
        }
        forgetTokens(forget);
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
                    // A screen that's gone asks no more, as a recreated one asks for itself.
                    if (isDestroyed()) {
                        return;
                    }
                    runOnUiThread(MainActivity.this::redrawLabels);
                    if (settings.getTokenImages()) {
                        loadTokenIcons(tokens);
                    }
                    List<String> searching = searching(tokens);
                    if (!searching.isEmpty()) {
                        askTokenLabelsSoon(searching);
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    t.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    // The tokens of the history shown, and the history they're of.
    private List<TransactionData> historyTokensOf;
    private Set<String> historyTokens = Collections.emptySet();

    // peekTokenLabels asks what the wallet already knows of the tokens in the transactions that it
    // doesn't hold any more, so that their rows read right, without having them load.
    private void peekTokenLabels(Wallet wallet, List<String> held) {
        // The history's tokens, collected again only when the history changes, as this runs on
        // the main thread at every ask, and a restored wallet can have thousands of transactions.
        List<TransactionData> history = mAdapter.getData();
        if (history != historyTokensOf) {
            historyTokens = TransactionList.tokens(history);
            historyTokensOf = history;
        }
        Set<String> hidden = settings.getHiddenTokens();
        Set<String> unknown = new HashSet<>();
        for (String category : historyTokens) {
            if (!TokenLabels.saidThisRun(category) && !hidden.contains(category)) {
                unknown.add(category);
            }
        }
        unknown.removeAll(held);
        for (List<String> part : Lists.partition(new ArrayList<>(unknown), TOKEN_LABELS_PER_CALL)) {
            Futures.addCallback(wallet.peekTokenMetadataAsync(part, false), new FutureCallback<List<TokenMeta>>() {
                @Override
                public void onSuccess(List<TokenMeta> tokens) {
                    TokenLabels.update(tokens);
                    if (!isDestroyed()) {
                        runOnUiThread(MainActivity.this::redrawLabels);
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    t.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    // searching returns the tokens the wallet is still looking for, of those that load by
    // themselves.
    private static List<String> searching(List<TokenMeta> tokens) {
        List<String> searching = new ArrayList<>();
        for (TokenMeta token : tokens) {
            if (token.loadsByItself && token.status == TokenMeta.Status.SEARCHING) {
                searching.add(token.category);
            }
        }
        return searching;
    }

    // askTokenLabelsSoon asks again in a while about the tokens the wallet is still looking for,
    // as it tells nothing by itself. The others it's asked about at each block.
    private void askTokenLabelsSoon(List<String> searching) {
        synchronized (labelsAsking) {
            labelsAsking.addAll(searching);
        }
        if (!isDestroyed() && labelsAskPending.compareAndSet(false, true)) {
            pagesHandler.postDelayed(() -> {
                labelsAskPending.set(false);
                List<String> asking;
                synchronized (labelsAsking) {
                    asking = new ArrayList<>(labelsAsking);
                    labelsAsking.clear();
                }
                Set<String> hidden = settings.getHiddenTokens();
                List<TokenBalance> shown = new ArrayList<>();
                for (TokenBalance token : settings.getLastTokens()) {
                    if (asking.contains(token.category) && !hidden.contains(token.category)) {
                        shown.add(token);
                    }
                }
                askTokenLabels(shown);
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
        // Once a frame, however many icons load in it, as each redraws every row.
        if (iconsRedrawPending) {
            return;
        }
        iconsRedrawPending = true;
        pages.postOnAnimation(() -> {
            iconsRedrawPending = false;
            tokenAdapter.iconsChanged();
            mAdapter.iconsChanged();
        });
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
        // Not before the saved transactions are read, or it would show at every start.
        boolean empty = mAdapter.getItemCount() == 0 && !txStore.stale();
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

    // fetchRates fetches the exchange rates off the main thread, as the screen opens, and shows
    // what the balance is worth once they come, on the main thread, if the screen is still there,
    // which it doesn't hold meanwhile.
    private void fetchRates() {
        ExchangeRates rates = exchangeRates;
        WeakReference<MainActivity> ref = new WeakReference<>(this);
        new Thread(() -> {
            try {
                rates.fetch();
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
            MainActivity screen = ref.get();
            if (screen != null) {
                screen.runOnUiThread(() -> screen.showBalance(screen.settings.getLastBalance()));
            }
        }, "exchange-rates").start();
    }

    private void maybeSendBackupReminder() {
        if (!settings.getBackupReminder() && settings.hasMnemonic()) {
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

    // MainListener listens to a wallet for the main screen. It keeps what the wallet tells, its
    // balance, tokens, tip and transactions, in the wallet's settings and its saved transactions,
    // whichever screen is in front, and shows it on the main screen that shows, if one does. A
    // wallet has one, which each main screen shares, as Android can recreate the screen while
    // the wallet loads or runs, and the wallet tells the listener its load began with: a screen
    // that loaded it again read its whole history again, ran what follows a load again, and could
    // leave the wallet telling the screen it replaced. What follows a load runs once, on the main
    // screen that shows then, or the next one to show.
    private static final class MainListener extends WalletEventListener {
        // Used on the main thread only. Its context is the application's, which lives as long.
        @SuppressLint("StaticFieldLeak")
        private static MainListener current;
        private static WeakReference<MainActivity> shown = new WeakReference<>(null);

        private final Wallet wallet;
        private final Settings settings;
        private final Context app;
        private final Handler main = new Handler(Looper.getMainLooper());
        // Whether the wallet loaded while no main screen showed.
        private boolean readyUnshown;

        private MainListener(Wallet wallet, Settings settings, Context app) {
            this.wallet = wallet;
            this.settings = settings;
            this.app = app;
        }

        // of returns the listener of the wallet, which the screen's settings are of.
        static MainListener of(Wallet wallet, MainActivity activity) {
            if (current == null || current.wallet != wallet) {
                current = new MainListener(wallet, activity.settings, activity.getApplicationContext());
            }
            return current;
        }

        // show has the listener show what the wallet tells on the screen, which shows now, and
        // runs what follows a load, if the wallet loaded while none showed.
        static void show(MainActivity activity) {
            shown = new WeakReference<>(activity);
            if (current != null && current.readyUnshown && current.wallet == activity.wallet) {
                current.readyUnshown = false;
                activity.walletReady();
            }
        }

        // hide has the listener stop showing what the wallet tells on the screen, which is gone.
        static void hide(MainActivity activity) {
            if (shown.get() == activity) {
                shown.clear();
            }
        }

        // shown returns the main screen that shows what this wallet tells, or null.
        private MainActivity shown() {
            MainActivity activity = shown.get();
            return activity != null && activity.wallet == wallet ? activity : null;
        }

        // onUi runs the change on the main thread, on the main screen that shows then, if one does.
        private void onUi(java.util.function.Consumer<MainActivity> change) {
            main.post(() -> {
                MainActivity activity = shown();
                if (activity != null) {
                    change.accept(activity);
                }
            });
        }

        @Override
        public void onWalletReady() {
            main.post(() -> {
                MainActivity activity = shown();
                if (activity == null) {
                    readyUnshown = true;
                } else {
                    activity.walletReady();
                }
            });
        }

        @Override
        public void onWalletError(String message) {
            onUi(activity -> Snackbar.make(activity.mCLayout,
                    activity.getString(R.string.wallet_error, message), Snackbar.LENGTH_INDEFINITE).show());
        }

        @Override
        public void onBalanceChange(long bal) {
            settings.setLastBalance(bal);
            onUi(activity -> {
                activity.showBalance(bal);
                if (bal > 0) {
                    activity.maybeSendBackupReminder();
                }
            });
        }

        @Override
        public void onTokenBalances(List<TokenBalance> tokens) {
            settings.setLastTokens(tokens);
            onUi(activity -> activity.showTokens(tokens));
        }

        // A new wallet's recovery phrase is kept before the wallet is created, and whether a
        // screen shows or not, as it can't be had again.
        @Override
        public void onCreatingWallet(String seed) {
            settings.setMnemonic(seed);
        }

        @Override
        public void onWalletCreated() {
            settings.setWalletInitialized(true);
        }

        @Override
        public void onGetTransactions(List<TransactionData> txs) {
            // This isn't the UI thread, and the file can be large: a rescan's replies would each
            // hold up the screen.
            String fiatCurrency = settings.getFiatCurrency();
            Network network = settings.getNetwork();
            for (TransactionData tx : txs) {
                tx.setFiatCurrency(fiatCurrency);
                tx.setFiatAmount(network.hasFiat
                        ? ExchangeRates.formattedOrEmpty(new Amount(tx.shownAmount()), Currency.getInstance(fiatCurrency)) : "");
            }
            // The wallet's transactions, which drops the ones it no longer has, such as one a
            // double spend replaced. The store keeps all of them, and the list leaves out those of
            // hidden tokens.
            TransactionStore store = TransactionStore.merge(app, network, txs);
            onUi(activity -> activity.showStore(store));
        }

        @Override
        public void onBlock(int blockHeight, String blockHash) {
            settings.setLastBlockHeight(blockHeight);
            settings.setLastBlockHash(blockHash);
            onUi(activity -> {
                activity.mAdapter.setBlockHeight(blockHeight);
                activity.showTokens(activity.settings.getLastTokens());
            });
        }

        @Override
        public void onScanProgress(int height) {
            settings.setScanHeight(height);
        }
    }
}
