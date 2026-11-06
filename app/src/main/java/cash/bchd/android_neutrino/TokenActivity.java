package cash.bchd.android_neutrino;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.FutureCallback;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import cash.bchd.android_neutrino.tokens.TokenIcons;
import cash.bchd.android_neutrino.tokens.TokenLabels;
import cash.bchd.android_neutrino.tokens.NftAdapter;
import cash.bchd.android_neutrino.wallet.Amount;
import cash.bchd.android_neutrino.wallet.Network;
import cash.bchd.android_neutrino.wallet.TokenBalance;
import cash.bchd.android_neutrino.wallet.TokenMeta;
import cash.bchd.android_neutrino.wallet.TransactionData;
import cash.bchd.android_neutrino.wallet.TransactionList;
import cash.bchd.android_neutrino.wallet.Wallet;
import cash.bchd.android_neutrino.wallet.WalletEventListener;
import walletrpc.Api;

/**
 * TokenActivity shows a token the wallet holds, under a header like the main screen's: its
 * identicon, how much the wallet holds of it, the BCH that its outputs carry above the floor, and
 * its token ID, which a tap copies. Below are the transactions that changed it, or, for its
 * collection, its NFTs. Its menu copies the token ID, and hides the token from the main screen.
 * While it's in front, it takes the wallet's notifications, which the main screen takes back when
 * it comes back.
 */
public class TokenActivity extends AppCompatActivity {

    static final String EXTRA_CATEGORY = "category";
    // Whether the screen shows the token's NFTs, rather than its history.
    static final String EXTRA_COLLECTION = "collection";

    private String category;
    private boolean collection;
    private Settings settings;
    private Network network;
    private CoordinatorLayout layout;
    private TokenHistoryAdapter history;
    private NftAdapter nfts;
    // The transactions the main screen saved, which keep the fiat values and memos that the
    // wallet doesn't have, with what the wallet told since.
    private List<TransactionData> transactions;
    private int blockHeight;
    private PopupWindow detailsPopup;
    private final WalletEventListener listener = new Listener(this);
    private final Handler handler = new Handler(Looper.getMainLooper());
    // Whether the user asked for the token's metadata on this screen.
    private boolean fetched;
    // Whether the description shows in full.
    private boolean descriptionOpen;
    // Whether the collection's NFTs can show, as they sort by the names the wallet says, and
    // shouldn't jump to their places once it has.
    private boolean nftsReady;
    // The wallet's tokens as last told, which saved ones can be behind of.
    private List<TokenBalance> tokens;
    private final Runnable askAgain = this::askWallet;
    private static final int DESCRIPTION_LINES = 3;
    private static final int DESCRIPTION_OPEN_LINES = 10;
    private static final long ASK_INTERVAL_MS = 5_000;

    /**
     * open opens the screen of a token on the Tokens page: that of its NFTs for a category that
     * has no fungible tokens, and the token's own screen otherwise.
     */
    static void open(Context context, TokenBalance token) {
        if (token.amount == 0 && token.nfts > 0) {
            openNfts(context, token);
            return;
        }
        Intent intent = new Intent(context, TokenActivity.class);
        intent.putExtra(EXTRA_CATEGORY, token.category);
        context.startActivity(intent);
    }

    /**
     * openNfts opens the screen of the token's only NFT, or else its collection's.
     */
    static void openNfts(Context context, TokenBalance token) {
        if (token.collection.size() == 1) {
            NftActivity.open(context, token.category, token.collection.get(0));
            return;
        }
        Intent intent = new Intent(context, TokenActivity.class);
        intent.putExtra(EXTRA_CATEGORY, token.category);
        intent.putExtra(EXTRA_COLLECTION, true);
        context.startActivity(intent);
    }

    /**
     * hide hides the token from the main screen, and goes back to it, closing the token's screens
     * on the way.
     */
    static void hide(Activity screen, Settings settings, String category) {
        settings.hideToken(category);
        Intent intent = new Intent(screen, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        screen.startActivity(intent);
        screen.finish();
    }

    /**
     * copyTokenId copies the token ID, and says so on the screen's layout.
     */
    static void copyTokenId(Activity screen, View layout, String category) {
        ClipboardManager clipboard = (ClipboardManager) screen.getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(screen.getString(R.string.source_text), category));
            Snackbar.make(layout, R.string.token_id_copied, Snackbar.LENGTH_LONG).show();
        }
    }

    /**
     * showTokenId shows a token ID on two lines of the same length, as it has 64 digits, and
     * copies it when tapped.
     */
    static void showTokenId(Activity screen, TextView view, View layout, String category) {
        view.setText(category.length() == 64
                ? screen.getString(R.string.token_id_lines, category.substring(0, 32), category.substring(32)) : category);
        view.setOnClickListener(v -> copyTokenId(screen, layout, category));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_token);
        SystemBars.apply(this);
        category = getIntent().getStringExtra(EXTRA_CATEGORY);
        collection = getIntent().getBooleanExtra(EXTRA_COLLECTION, false);
        settings = Settings.get(this);
        network = settings.getNetwork();
        Networks.showBadge(this, network);
        layout = findViewById(R.id.tokenLayout);

        Toolbar toolbar = findViewById(R.id.tokenToolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        tokens = settings.getLastTokens();
        showIcon();
        showTokenId(this, findViewById(R.id.tokenId), layout, category);
        findViewById(R.id.tokenDescription).setOnClickListener(v -> toggleDescription((TextView) v));

        RecyclerView list = findViewById(R.id.tokenHistory);
        list.setLayoutManager(new LinearLayoutManager(this));
        // With the application's context, as the main screen's lists have.
        list.addItemDecoration(new DividerItemDecoration(getApplicationContext(), DividerItemDecoration.VERTICAL));
        if (collection) {
            nfts = new NftAdapter(this, category, nft -> NftActivity.open(this, category, nft));
            list.setAdapter(nfts);
            list.setItemAnimator(null);
            nftsReady = TokenLabels.hasNftTypes(category);
        } else {
            history = new TokenHistoryAdapter(this, category, tx ->
                    detailsPopup = TransactionDetailsPopup.show(this, layout, tx, blockHeight, network));
            list.setAdapter(history);
            transactions = new TransactionStore(this, network).getData();
            Collections.sort(transactions, Collections.reverseOrder());
            blockHeight = settings.getLastBlockHeight();
            history.setTransactions(transactions, blockHeight);
        }
        showToken(tokens);
        askWallet();
    }

    // showIcon shows the token's icon, which is its identicon until one is loaded.
    private void showIcon() {
        TokenIcons.configure(settings.getTokenImages(), settings.getLoadedIcons());
        ImageView icon = findViewById(R.id.tokenIcon);
        icon.setImageBitmap(TokenIcons.bitmap(this, category,
                getResources().getDimensionPixelSize(R.dimen.token_screen_icon)));
    }

    // askWallet shows what the wallet can say of the token now.
    private void askWallet() {
        Wallet wallet = Wallet.getInstance();
        if (wallet == null || !wallet.isRunning()) {
            showLabels();
            showNftsAnyway();
            return;
        }
        Futures.addCallback(wallet.tokenMetadataAsync(Collections.singletonList(category), true),
                new FutureCallback<List<TokenMeta>>() {
                    @Override
                    public void onSuccess(List<TokenMeta> metadata) {
                        boolean changed = TokenLabels.update(metadata);
                        runOnUiThread(() -> {
                            if (isDestroyed()) {
                                return;
                            }
                            showLabels();
                            nftsReady = true;
                            showToken(tokens);
                            if (changed && history != null) {
                                history.notifyItemRangeChanged(0, history.getItemCount());
                            }
                            if (nfts != null) {
                                nfts.labelsChanged();
                            }
                            if (!metadata.isEmpty() && metadata.get(0).status == TokenMeta.Status.SEARCHING
                                    && (metadata.get(0).loadsByItself || fetched)) {
                                handler.removeCallbacks(askAgain);
                                handler.postDelayed(askAgain, ASK_INTERVAL_MS);
                            }
                        });
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        t.printStackTrace();
                        runOnUiThread(TokenActivity.this::showNftsAnyway);
                    }
                }, MoreExecutors.directExecutor());
    }

    // showNftsAnyway shows the NFTs without waiting for names that aren't coming.
    private void showNftsAnyway() {
        if (!nftsReady) {
            nftsReady = true;
            showToken(tokens);
        }
    }

    // toggleDescription shows all of the description, which scrolls in place if it's long, as the
    // screen's list still has to show, or goes back to its first lines.
    private void toggleDescription(TextView description) {
        descriptionOpen = !descriptionOpen;
        description.setMaxLines(descriptionOpen ? DESCRIPTION_OPEN_LINES : DESCRIPTION_LINES);
        description.setEllipsize(descriptionOpen ? null : TextUtils.TruncateAt.END);
        description.setMovementMethod(descriptionOpen ? ScrollingMovementMethod.getInstance() : null);
        description.scrollTo(0, 0);
        if (!descriptionOpen) {
            // Clearing the movement leaves the view unclickable, and text that can't ellipsize.
            description.setClickable(true);
            description.setText(description.getText().toString());
        }
    }

    // showLabels shows the token's name, how far to trust it, and its description, which are
    // only there once the wallet has them.
    private void showLabels() {
        TokenMeta token = TokenLabels.get(category);
        boolean has = token != null && token.hasMetadata;
        TextView name = findViewById(R.id.tokenName);
        name.setText(has ? token.name + " (" + token.symbol + ")" : "");
        name.setVisibility(has ? View.VISIBLE : View.GONE);
        TextView description = findViewById(R.id.tokenDescription);
        description.setText(has ? token.description : "");
        description.setVisibility(has && !token.description.isEmpty() ? View.VISIBLE : View.GONE);
        TextView badge = findViewById(R.id.tokenBadge);
        int text = TokenLabels.badge(category);
        badge.setVisibility(text == 0 ? View.GONE : View.VISIBLE);
        if (text != 0) {
            badge.setText(text);
        }
    }

    /**
     * fetchMetadata has the wallet find a token's metadata, or find it again.
     */
    static void fetchMetadata(Activity screen, View layout, String category, Runnable asked) {
        Wallet wallet = Wallet.getInstance();
        if (wallet == null || !wallet.isRunning()) {
            return;
        }
        Futures.addCallback(wallet.refreshTokenMetadataAsync(category),
                new FutureCallback<Api.RefreshTokenMetadataResponse>() {
                    @Override
                    public void onSuccess(Api.RefreshTokenMetadataResponse reply) {
                        screen.runOnUiThread(() -> {
                            Snackbar.make(layout, R.string.fetching_token_metadata, Snackbar.LENGTH_LONG).show();
                            asked.run();
                        });
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        screen.runOnUiThread(() -> Snackbar.make(layout,
                                R.string.fetch_token_metadata_failed, Snackbar.LENGTH_LONG).show());
                    }
                }, MoreExecutors.directExecutor());
    }

    /**
     * loadIcon loads a token's icon and tells done on the UI thread if it did. Only the user asks
     * for this, or the setting that shows all icons, as the icon's host sees who asks.
     */
    static void loadIcon(Activity screen, Settings settings, View layout, String category, Runnable done) {
        Wallet wallet = Wallet.getInstance();
        TokenMeta token = TokenLabels.get(category);
        if (wallet == null || !wallet.isRunning()) {
            return;
        }
        // The NFTs' art counts as the token's images, whatever becomes of the icon.
        boolean art = token != null && hasNftArt(token);
        if (art) {
            settings.addLoadedIcon(category);
        }
        if (art && token.iconUri.isEmpty()) {
            Snackbar.make(layout, R.string.token_no_icon, Snackbar.LENGTH_LONG).show();
            done.run();
            return;
        }
        if (token == null || token.iconUri.isEmpty()) {
            Snackbar.make(layout, R.string.token_no_icon, Snackbar.LENGTH_LONG).show();
            return;
        }
        // A collection still has its NFTs' art, so its icon missing is no failure of what was asked.
        int failed = art ? R.string.token_icon_unavailable : R.string.token_icon_failed;
        Futures.addCallback(wallet.tokenIconAsync(token.iconUri), new FutureCallback<byte[]>() {
            @Override
            public void onSuccess(byte[] data) {
                boolean saved = TokenIcons.save(screen, category, data);
                screen.runOnUiThread(() -> {
                    if (saved) {
                        settings.addLoadedIcon(category);
                        done.run();
                    } else {
                        Snackbar.make(layout, failed, Snackbar.LENGTH_LONG).show();
                        if (art) {
                            done.run();
                        }
                    }
                });
            }

            @Override
            public void onFailure(Throwable t) {
                screen.runOnUiThread(() -> {
                    Snackbar.make(layout, failed, Snackbar.LENGTH_LONG).show();
                    if (art) {
                        done.run();
                    }
                });
            }
        }, MoreExecutors.directExecutor());
    }

    // hasNftArt returns whether the token's NFTs have art of their own.
    private static boolean hasNftArt(TokenMeta token) {
        for (TokenMeta.NftType type : token.nftTypes) {
            if (!type.iconUri.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onStart() {
        super.onStart();
        // The wallet tells this screen what changes while it's in front, and tells it again now
        // what it may have missed.
        Wallet wallet = Wallet.getInstance();
        if (wallet != null && wallet.isRunning()) {
            wallet.setMainListener(listener);
            new Thread(() -> {
                try {
                    wallet.refresh(listener);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, "wallet-refresh").start();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (nfts != null) {
            nfts.stop();
        }
        if (detailsPopup != null) {
            detailsPopup.dismiss();
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.token, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            finish();
            return true;
        } else if (itemId == R.id.copyTokenId) {
            copyTokenId(this, layout, category);
            return true;
        } else if (itemId == R.id.fetchTokenMetadata) {
            fetched = true;
            fetchMetadata(this, layout, category, this::askWallet);
            return true;
        } else if (itemId == R.id.loadTokenIcon) {
            loadIcon(this, settings, layout, category, () -> {
                showIcon();
                if (nfts != null) {
                    nfts.iconsChanged();
                }
            });
            return true;
        } else if (itemId == R.id.hideToken) {
            hide(this, settings, category);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // showToken shows how much the wallet holds of the token, among the token balances: its
    // fungible amount, its NFTs, or both, on a line of their own, which opens them, and the BCH
    // that its outputs carry above the floor, if any. The collection's screen shows its NFTs
    // only, and lists them. The wallet holds none of a token it has sent away.
    private void showToken(List<TokenBalance> tokens) {
        TokenBalance found = TokenBalance.find(tokens, category);
        TokenBalance token = found != null ? found
                : new TokenBalance(category, 0, 0, 0, Collections.emptyList());
        TextView amount = findViewById(R.id.tokenAmount);
        TextView nftLine = findViewById(R.id.tokenNfts);
        TextView bch = findViewById(R.id.tokenBch);
        String nftText = getResources().getQuantityString(R.plurals.token_nfts, token.nfts, token.nfts);
        if (collection) {
            amount.setText(nftText);
            nftLine.setVisibility(View.GONE);
            bch.setVisibility(View.GONE);
            if (nftsReady) {
                nfts.setNfts(token.collection);
            }
            return;
        }
        if (token.amount == 0 && token.nfts > 0) {
            amount.setText(nftText);
            nftLine.setVisibility(View.GONE);
        } else {
            amount.setText(TokenLabels.amount(category, token.amount));
            nftLine.setText(getString(R.string.token_nfts_open, nftText));
            nftLine.setOnClickListener(v -> openNfts(this, token));
            nftLine.setVisibility(token.nfts > 0 ? View.VISIBLE : View.GONE);
        }
        bch.setText(getString(R.string.token_bch_held, new Amount(token.satoshisAboveFloor).toString()));
        bch.setVisibility(token.satoshisAboveFloor > 0 ? View.VISIBLE : View.GONE);
    }

    // Listener updates the screen with what the wallet tells it, on the UI thread, while the
    // screen is there; the wallet keeps it only until the main screen takes its notifications
    // back.
    private static final class Listener extends WalletEventListener {
        private final WeakReference<TokenActivity> screen;

        Listener(TokenActivity screen) {
            this.screen = new WeakReference<>(screen);
        }

        private void update(Consumer<TokenActivity> change) {
            TokenActivity activity = screen.get();
            if (activity != null) {
                activity.runOnUiThread(() -> change.accept(activity));
            }
        }

        @Override
        public void onBlock(int blockHeight, String blockHash) {
            update(activity -> {
                activity.blockHeight = blockHeight;
                if (activity.history != null) {
                    activity.history.setBlockHeight(blockHeight);
                }
            });
        }

        @Override
        public void onTokenBalances(List<TokenBalance> tokens) {
            // The main screen saves them while it listens, which it isn't now.
            TokenActivity saving = screen.get();
            if (saving != null) {
                saving.settings.setLastTokens(tokens);
            }
            update(activity -> {
                activity.tokens = tokens;
                activity.showToken(tokens);
                activity.askWallet();
            });
        }

        @Override
        public void onGetTransactions(List<TransactionData> txs, int blockHeight) {
            update(activity -> {
                if (activity.history != null) {
                    activity.transactions = TransactionList.rebuild(activity.transactions, txs);
                    activity.history.setTransactions(activity.transactions, activity.blockHeight);
                }
            });
        }
    }
}
