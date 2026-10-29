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
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.TextUtils;
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.lang.ref.WeakReference;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Currency;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import cash.bchd.android_neutrino.wallet.AddressListener;
import cash.bchd.android_neutrino.wallet.Amount;
import cash.bchd.android_neutrino.wallet.BitcoinPaymentURI;
import cash.bchd.android_neutrino.wallet.Config;
import cash.bchd.android_neutrino.wallet.ExchangeRates;
import cash.bchd.android_neutrino.wallet.Migration;
import cash.bchd.android_neutrino.wallet.Network;
import cash.bchd.android_neutrino.wallet.SyncStatus;
import cash.bchd.android_neutrino.wallet.TransactionData;
import cash.bchd.android_neutrino.wallet.TransactionList;
import cash.bchd.android_neutrino.wallet.Wallet;
import cash.bchd.android_neutrino.wallet.WalletEventListener;
import walletrpc.Api;

import static cash.bchd.android_neutrino.SendActivity.RC_BARCODE_CAPTURE;


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
    // Whether this activity listens to the wallet's events.
    boolean walletAttached;
    // Shows the wallet's sync status while this activity is in front.
    private ScheduledExecutorService syncStatusPoller;
    // Set while the user is asked to let the app reach the local network, which the wallet's
    // start waits for.
    private boolean askingForLocalNetwork;

    private static final long DONATION_REMINDER_INTERVAL = TimeUnit.DAYS.toMillis(45);
    private static final long SYNC_STATUS_POLL_MILLIS = 2000;
    private static final int RC_UPGRADE_PIN = 9003;
    private static final int RC_LOCAL_NETWORK = 9005;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SystemBars.apply(this);
        cancelCloseTimer();
        layoutManager = new LinearLayoutManager(this);
        this.settings = Settings.load(this);
        Network network = settings.getNetwork();
        Networks.showBadge(this, network);
        this.txStore = new TransactionStore(this, network);
        this.mSwipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
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
        RecyclerView recyclerView = findViewById(R.id.txRecylerView);
        recyclerView.setHasFixedSize(true);
        DividerItemDecoration decor = new DividerItemDecoration(getApplicationContext(), DividerItemDecoration.VERTICAL);
        recyclerView.addItemDecoration(decor);
        // use a linear layout manager
        recyclerView.setLayoutManager(layoutManager);
        // specify an adapter
        List<TransactionData> txs = txStore.getData();
        Collections.sort(txs, Collections.reverseOrder());
        if (txs.size() > 0) {
            TextView bchPlease = findViewById(R.id.bchPlease);
            bchPlease.setVisibility(View.GONE);
        }
        mAdapter = new TransactionAdapter(txs, this, mCLayout, settings.getLastBlockHeight(), network);
        recyclerView.setAdapter(mAdapter);
        fab.setOnClickListener(view -> toggleFABMenu());
        listenForMenuSlides();
        fabQR.setOnClickListener(v -> {
            fab.bringToFront();
            displayQRPopup();
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
            Intent newIntent = new Intent();
            newIntent.putExtra("qrdata", SettingsFragment.DONATE_URI);
            this.onActivityResult(RC_BARCODE_CAPTURE, RESULT_OK, newIntent);
        }
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
        });
    }

    public void createWallet() {
        if (askingForLocalNetwork) {
            return;
        }
        if (Wallet.getInstance() == null) {
            String[] peers = settings.getPeerList();
            String[] none = new String[0];
            boolean peersOnly = settings.getPeersOnly() && peers.length > 0;
            String bchdIP = settings.getBchdIP();
            if (!mayStartWallet(peers, bchdIP)) {
                return;
            }
            long birthday = settings.getWalletBirthday();
            if (birthday == 0) {
                birthday = System.currentTimeMillis() / 1000;
                settings.setWalletBirthday(birthday);
            }

            Migration migration = new Migration(settings.getRepoVersion());
            int newRepoVersion = migration.MigrateUp(this);
            settings.setRepoVersion(newRepoVersion);

            Config cfg = new Config(getDataDir().getPath(), settings.getNetwork(), !settings.getWalletInitialized(),
                    bchdIP.equals(""), settings.getBlocksOnly(), peersOnly ? peers : none, peersOnly ? none : peers,
                    settings.getBchdIP(), settings.getBchdUsername(),
                    settings.getBchdPassword(), settings.getBchdCert(), birthday);
            wallet = new Wallet(this, cfg);
            wallet.start();
            new StartWalletTask(this).execute(wallet);
        } else if (!walletAttached) {
            // The activity was recreated while the wallet kept running.
            wallet = Wallet.getInstance();
            new StartWalletTask(this).execute(wallet);
        }
        walletAttached = true;
    }

    // mayStartWallet returns whether the wallet can start now. Android 17 and newer let the app
    // reach the local network only with the user's permission, so a wallet set to connect to
    // nodes there asks for it first, once for those nodes, and starts once the user has
    // answered, since a bchd node that it can't reach stops it.
    private boolean mayStartWallet(String[] peers, String bchdIP) {
        List<String> nodes = new ArrayList<>(Arrays.asList(peers));
        if (!bchdIP.isEmpty()) {
            nodes.add(bchdIP);
        }
        List<String> local = LocalNetwork.local(nodes);
        if (local.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED) {
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
        cancelCloseTimer();
        if (settings != null) {
            // Start the wallet again if the close timer stopped it while the app
            // was in the background.
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
        Intent intent = new Intent(this, ScannerActivity.class);
        startActivityForResult(intent, RC_BARCODE_CAPTURE);
    }

    private void displayQRPopup() {
        String lastAddr = "";
        if (!wallet.isRunning()) {
            lastAddr = settings.getLastAddress();
            if (lastAddr.equals("")) {
                Snackbar snackbar = Snackbar.make(mCLayout, R.string.wallet_is_not_loaded_yet, Snackbar.LENGTH_LONG);
                snackbar.show();
                return;
            }
        }
        toggleFABMenu();
        LayoutInflater layoutInflater = (LayoutInflater) MainActivity.this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View customView = layoutInflater.inflate(R.layout.qrpopup, mCLayout, false);
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
            String addrURI = wallet.uriPrefix() + addr;
            Bitmap bitmap = QrCodes.encode(addrURI, smallerDimension);
            qrImage = customView.findViewById(R.id.qrCodeView);
            qrImage.setImageBitmap(bitmap);
            qrImage.setOnClickListener(v -> copyToClipboard(addr));
            addrText = customView.findViewById(R.id.address);
            addrText.setText(addr);
            addrText.setOnClickListener(v -> copyToClipboard(addr));
            wallet.listenAddress(addr, new AddressListener() {
                @Override
                public void onPaymentReceived(long amount) {
                    if (getApplicationContext() != null) {
                        runOnUiThread(() -> {
                            Vibrator vibrator = (Vibrator) getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
                            if (vibrator != null) {
                                vibrator.vibrate(500);
                            }
                            LinearLayout qrLayout = customView.findViewById(R.id.qrCodeLayout);
                            int h = qrLayout.getHeight();
                            qrLayout.setHorizontalGravity(Gravity.CENTER_HORIZONTAL);
                            qrLayout.setVerticalGravity(Gravity.CENTER_VERTICAL);
                            TextView addrHelperText = customView.findViewById(R.id.addrHelpText);
                            qrImage.setVisibility(View.GONE);
                            addrText.setVisibility(View.GONE);
                            addrHelperText.setVisibility(View.GONE);
                            final GifView showGifView = new GifView(getApplicationContext());
                            showGifView.setGifImageDrawableId(R.drawable.coinflip);
                            showGifView.drawGif();
                            showGifView.setForegroundGravity(Gravity.CENTER);
                            ViewGroup.LayoutParams params = qrLayout.getLayoutParams();
                            Double dh = (double) h;
                            Double truncatedH = dh * 0.8;
                            params.height = truncatedH.intValue();
                            params.width = h;
                            qrLayout.requestLayout();
                            qrLayout.addView(showGifView);
                        });
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        fabSettings.animate().translationY(-getResources().getDimension(R.dimen.standard_65));
        fabReceive.animate().translationY(-getResources().getDimension(R.dimen.standard_120));
        fabSend.animate().translationY(-getResources().getDimension(R.dimen.standard_175));
        fabScan.animate().translationY(-getResources().getDimension(R.dimen.standard_230));
        fabQR.animate().translationY(-getResources().getDimension(R.dimen.standard_285));
    }

    private void closeFABMenu() {
        isFabOpen = false;
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_BARCODE_CAPTURE) {
            if (resultCode == RESULT_OK) {
                if (data != null) {
                    String qrdata = data.getStringExtra("qrdata");
                    BitcoinPaymentURI uri = BitcoinPaymentURI.parse(qrdata);
                    if (uri != null && uri.getR() != null) {
                        PaymentRequests.pay(this, mCLayout, wallet, qrdata, settings.getFiatCurrency(), settings.getFeePerByte());
                    } else {
                        Intent intent = new Intent(this, SendActivity.class);
                        intent.putExtra("fiatCurrency", this.settings.getFiatCurrency());
                        intent.putExtra("feePerByte", this.settings.getFeePerByte());
                        intent.putExtra("qrdata", qrdata);
                        startActivity(intent);
                    }
                }
            } else if (resultCode != RESULT_CANCELED) {
                Snackbar snackbar = Snackbar.make(mCLayout, R.string.barcode_read_error, Snackbar.LENGTH_LONG);
                snackbar.show();
            }
        } else if (requestCode == RC_UPGRADE_PIN) {
            if (resultCode == RESULT_OK && data != null) {
                try {
                    upgradeFrom(Wallet.SHA256(data.getStringExtra("pin")));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
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
                        Intent intent = new Intent(this, PinActivity.class);
                        startActivityForResult(intent, RC_UPGRADE_PIN);
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

    // openLaunchPayment opens the payment link that started the app, and removes it from the
    // intent itself, of which getExtras returns a copy, so that it opens once rather than every
    // time this screen comes back.
    private void openLaunchPayment() {
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        String uri = intent.getStringExtra("uri");
        if (uri != null && !uri.equals("")) {
            intent.removeExtra("uri");
            Intent newIntent = new Intent();
            newIntent.putExtra("qrdata", uri);
            onActivityResult(RC_BARCODE_CAPTURE, RESULT_OK, newIntent);
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("default", "default", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("default channel");
            notificationManager.createNotificationChannel(channel);
        }
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
                        if (mainActivity.getApplicationContext() != null) {
                            mainActivity.runOnUiThread(() -> {
                                MainActivity mainActivity2 = mainActivityRef.get();
                                if (mainActivity2 == null) {
                                    return;
                                }
                                String fiatCurrency = mainActivity2.settings.getFiatCurrency();
                                for (TransactionData tx : txs) {
                                    tx.setFiatCurrency(fiatCurrency);
                                    String formattedFiat = "";
                                    if (mainActivity2.exchangeRates != null) {
                                        try {
                                            formattedFiat = mainActivity2.exchangeRates.getFormattedAmountInFiat(new Amount(tx.getAmount()), Currency.getInstance(fiatCurrency));
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                    tx.setFiatAmount(formattedFiat);
                                }
                                // Show the transactions the wallet has, which drops the ones it no
                                // longer has, such as one a double spend replaced.
                                List<TransactionData> list = TransactionList.rebuild(mainActivity2.mAdapter.getData(), txs);
                                TextView bchPlease = mainActivity2.findViewById(R.id.bchPlease);
                                bchPlease.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                                // A transaction added at the top goes above the first row shown, so
                                // the list scrolls to show it when it showed the newest before.
                                LinearLayoutManager rows = (LinearLayoutManager) mainActivity2.layoutManager;
                                boolean atTop = rows.findFirstVisibleItemPosition() <= 0;
                                mainActivity2.mAdapter.setNewData(list);
                                if (atTop) {
                                    rows.scrollToPosition(0);
                                }
                                mainActivity2.txStore.setData(list);
                                try {
                                    mainActivity2.txStore.save(mainActivity2.getApplicationContext());
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });
                        }
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
                wallet.loadWallet(listener, mainActivity.settings.getMnemonic());
                mainActivity.mSwipeRefreshLayout.setOnRefreshListener(() -> {
                    try {
                        Api.NetworkResponse net = wallet.network();
                        listener.onBlock(net.getBestHeight(), net.getBestBlock());
                        wallet.getTransactions(listener);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    mainActivity.mSwipeRefreshLayout.setRefreshing(false);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
