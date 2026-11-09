package cash.bchd.android_neutrino;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.snackbar.Snackbar;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cash.bchd.android_neutrino.tokens.NftArt;
import cash.bchd.android_neutrino.tokens.TokenIcons;
import cash.bchd.android_neutrino.tokens.TokenLabels;
import cash.bchd.android_neutrino.wallet.Amount;
import cash.bchd.android_neutrino.wallet.Network;
import cash.bchd.android_neutrino.wallet.TokenBalance;
import cash.bchd.android_neutrino.wallet.TokenMeta;
import cash.bchd.android_neutrino.wallet.TokenNft;
import cash.bchd.android_neutrino.wallet.TransactionData;
import cash.bchd.android_neutrino.wallet.Wallet;

/**
 * NftActivity shows one of the NFTs the wallet holds, under a header like a token's screen: its
 * own art, or else its token's icon, what it can do, explained when it can do more than be held, and the BCH that
 * its output carries above the floor. Below are its commitment and token ID, which a tap copies,
 * and the transaction it came in, which a tap opens. Its menu copies the token ID, and hides the
 * NFT's whole token from the main screen.
 */
public class NftActivity extends AppCompatActivity {

    // The output that holds the NFT.
    static final String EXTRA_TXID = "txid";
    static final String EXTRA_INDEX = "index";

    private String category;
    private String txid;
    private int index;
    private Settings settings;
    private Network network;
    private CoordinatorLayout layout;
    private PopupWindow detailsPopup;
    // The NFT's commitment, once it's known.
    private String commitment;
    private NftArt art;
    // How many more times to look for what a fetch brought.
    private int refreshesLeft;
    private static final long FETCH_REFRESH_MS = 5_000;
    private static final int FETCH_REFRESHES = (int) (TokenActivity.FETCH_WAIT_MS / FETCH_REFRESH_MS);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final ExecutorService STORE_READER = Executors.newSingleThreadExecutor();
    // Whether to ask for the collection's NFT types though the screen has some, as a fetch is over.
    private boolean typesAgain;
    // The wallet stops while the app is away, and takes a moment when it comes back to this screen.
    private final Runnable walletReady = () -> handler.post(this::refresh);
    // One thread for the screens' questions, which wait on the wallet, so that they take turns.
    private static final ExecutorService ASKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "wallet-nfts");
        thread.setDaemon(true);
        return thread;
    });
    private PopupWindow artPopup;

    /**
     * open opens the screen of one of the token's NFTs.
     */
    static void open(Context context, String category, TokenNft nft) {
        Intent intent = new Intent(context, NftActivity.class);
        intent.putExtra(TokenActivity.EXTRA_CATEGORY, category);
        intent.putExtra(EXTRA_TXID, nft.txid);
        intent.putExtra(EXTRA_INDEX, nft.index);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nft);
        SystemBars.apply(this);
        category = getIntent().getStringExtra(TokenActivity.EXTRA_CATEGORY);
        txid = getIntent().getStringExtra(EXTRA_TXID);
        index = getIntent().getIntExtra(EXTRA_INDEX, 0);
        settings = Settings.get(this);
        network = settings.getNetwork();
        Networks.showBadge(this, network);
        layout = findViewById(R.id.nftLayout);
        art = new NftArt(this, (loadedCategory, loadedCommitment) -> showIcon());
        findViewById(R.id.nftArt).setOnClickListener(v -> openArt());
        DescriptionToggle.attach(findViewById(R.id.nftDescription), false);

        Toolbar toolbar = findViewById(R.id.nftToolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        showIcon();
        TokenActivity.showTokenId(this, findViewById(R.id.nftTokenId), layout, category);
        showReceived();
        showNft(settings.getLastTokens());

        refresh();
    }

    // refresh shows the wallet's NFTs as they are now, which the main screen may not have told
    // since, with what the wallet can say of them. The collection's NFT types, which can be many,
    // are asked for when the screen has none, and once more when a fetch is over.
    private void refresh() {
        Wallet wallet = Wallet.getInstance();
        if (wallet == null || !wallet.isRunning()) {
            return;
        }
        boolean types = asksForTypes(typesAgain, TokenLabels.hasNftTypes(category));
        typesAgain = false;
        ASKER.execute(() -> {
            try {
                List<TokenBalance> tokens = wallet.tokenBalances();
                boolean changed = false;
                try {
                    changed = TokenLabels.update(
                            wallet.tokenMetadataAsync(Collections.singletonList(category), types).get(), types);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                boolean readOver = changed && TokenActivity.readOver(category);
                runOnUiThread(() -> {
                    if (isDestroyed()) {
                        return;
                    }
                    showNft(tokens);
                    // After Fetch metadata, until what the registry read found shows.
                    if (readOver && refreshesLeft > 0) {
                        refreshesLeft = 0;
                        typesAgain = true;
                        refresh();
                    } else if (refreshesLeft > 0) {
                        refreshesLeft--;
                        typesAgain = refreshesLeft == 0;
                        handler.postDelayed(this::refresh, FETCH_REFRESH_MS);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * asksForTypes returns whether to ask the wallet for the collection's NFT types: when they're
     * to be asked for again, or the screen has none.
     */
    static boolean asksForTypes(boolean again, boolean has) {
        return again || !has;
    }

    @Override
    protected void onStart() {
        super.onStart();
        Wallet.addReadyListener(walletReady);
        if (Wallet.getInstance() == null && settings.getWalletInitialized()) {
            // Android brought this screen back without the main screen that starts the wallet.
            WalletStarter.restart(this);
        }
    }

    @Override
    protected void onStop() {
        Wallet.removeReadyListener(walletReady);
        if (artPopup != null) {
            artPopup.dismiss();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        art.stop();
        if (artPopup != null) {
            artPopup.dismiss();
        }
        if (detailsPopup != null) {
            detailsPopup.dismiss();
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.token, menu);
        // Hiding is of the whole token, which on this screen could read as only this NFT.
        menu.findItem(R.id.hideToken).setTitle(R.string.hide_collection);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            finish();
            return true;
        } else if (itemId == R.id.copyTokenId) {
            TokenActivity.copyTokenId(this, layout, category);
            return true;
        } else if (itemId == R.id.fetchTokenMetadata) {
            TokenActivity.fetchMetadata(this, layout, category, () -> {
                refreshesLeft = FETCH_REFRESHES;
                refresh();
            });
            return true;
        } else if (itemId == R.id.loadTokenIcon) {
            TokenActivity.loadIcon(this, settings, layout, category, () -> {
                showIcon();
                art.retry();
                art.forUser(category);
                art.request(category, commitment);
            });
            return true;
        } else if (itemId == R.id.hideToken) {
            TokenActivity.hide(this, settings, category);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // showIcon shows the NFT's art, or its token's icon, which is its identicon until one is loaded.
    private void showIcon() {
        TokenIcons.configure(settings.getTokenImages(), settings.getLoadedIcons());
        ImageView icon = findViewById(R.id.nftIcon);
        int size = getResources().getDimensionPixelSize(R.dimen.nft_screen_icon);
        if (commitment == null) {
            TokenIcons.showIcon(icon, category, size);
        } else {
            TokenIcons.showNft(icon, category, commitment, size);
        }
        String uri = artUri();
        boolean moves = uri != null && TokenIcons.moves(this, category, commitment, uri);
        findViewById(R.id.nftPlay).setVisibility(moves ? View.VISIBLE : View.GONE);
        // It's only a button with art to open, and what a reader says is what a tap does.
        View artView = findViewById(R.id.nftArt);
        artView.setClickable(uri != null);
        artView.setContentDescription(uri == null ? null : getString(moves ? R.string.nft_play : R.string.nft_art_open));
    }

    // artUri returns the URI of the art to show large, if the NFT's art is there to show: what
    // moves, or else the full image, or else the icon. Some NFTs have only one of them.
    private String artUri() {
        TokenMeta token = TokenLabels.get(category);
        TokenMeta.NftType type = token == null || commitment == null ? null : token.nftType(commitment);
        if (type == null || !TokenIcons.showing(category) || TokenLabels.isLookAlike(category)
                || !TokenIcons.hasNft(this, category, commitment)) {
            return null;
        }
        boolean icon = !type.iconUri.isEmpty();
        if (icon && (type.imageUri.isEmpty() || TokenIcons.moves(this, category, commitment, type.iconUri))) {
            return type.iconUri;
        }
        return type.imageUri.isEmpty() ? null : type.imageUri;
    }

    // fullUri returns the URI to fetch the art in full from, or null if what's saved is that file,
    // so that a still image isn't downloaded twice.
    private String fullUri(String uri) {
        TokenMeta token = TokenLabels.get(category);
        TokenMeta.NftType type = token == null ? null : token.nftType(commitment);
        boolean thumbnail = type != null && (type.iconUri.isEmpty() || uri.equals(type.iconUri));
        return thumbnail && !TokenIcons.moves(this, category, commitment, uri)
                && TokenIcons.savedFrom(this, category, commitment, uri) ? null : uri;
    }

    // openArt shows the art large, if it's there to show.
    private void openArt() {
        String uri = artUri();
        if (uri != null && (artPopup == null || !artPopup.isShowing())) {
            artPopup = NftArtPopup.show(this, layout, category, commitment, fullUri(uri));
        }
    }

    // showNft shows the NFT, among the token balances, or keeps what it shows if they don't have
    // it, as when it has moved since.
    private void showNft(List<TokenBalance> tokens) {
        TokenBalance token = TokenBalance.find(tokens, category);
        TokenNft nft = token != null ? token.findNft(txid, index) : null;
        if (nft == null) {
            return;
        }
        commitment = nft.commitment;
        showIcon();
        art.request(category, commitment);
        TextView nameView = findViewById(R.id.nftName);
        String name = TokenLabels.nftName(category, nft.commitment);
        TokenMeta meta = TokenLabels.get(category);
        String shown = name != null ? name : meta != null && meta.hasMetadata ? meta.name : null;
        nameView.setText(shown);
        nameView.setVisibility(shown == null ? View.GONE : View.VISIBLE);
        TextView badgeView = findViewById(R.id.nftBadge);
        int badge = TokenLabels.badge(category);
        badgeView.setVisibility(badge == 0 ? View.GONE : View.VISIBLE);
        if (badge != 0) {
            badgeView.setText(badge);
        }
        showDetails(meta, nft.commitment);
        TextView kind = findViewById(R.id.nftKind);
        TextView explained = findViewById(R.id.nftKindExplained);
        switch (nft.capability) {
            case MINTING:
                kind.setText(R.string.nft_kind_minting);
                kind.setVisibility(View.VISIBLE);
                explained.setText(R.string.nft_minting_explained);
                explained.setVisibility(View.VISIBLE);
                break;
            case MUTABLE:
                kind.setText(R.string.nft_kind_mutable);
                kind.setVisibility(View.VISIBLE);
                explained.setText(R.string.nft_mutable_explained);
                explained.setVisibility(View.VISIBLE);
                break;
            default:
                kind.setVisibility(View.GONE);
                explained.setVisibility(View.GONE);
        }
        TextView bch = findViewById(R.id.nftBch);
        bch.setText(getString(R.string.token_bch_held, new Amount(nft.satoshisAboveFloor()).toString()));
        bch.setVisibility(nft.satoshisAboveFloor() > 0 ? View.VISIBLE : View.GONE);

        // In hex, as it is: only the token's metadata can say how to read it.
        TextView commitment = findViewById(R.id.nftCommitment);
        if (nft.commitment.isEmpty()) {
            commitment.setTypeface(Typeface.DEFAULT);
            commitment.setText(R.string.nft_no_commitment);
            commitment.setOnClickListener(null);
        } else {
            commitment.setTypeface(Typeface.MONOSPACE);
            commitment.setText(nft.commitment);
            commitment.setOnClickListener(v -> copyCommitment(nft.commitment));
        }
    }

    // oneLine puts an attribute's text on one line, as a registry's can break it anywhere.
    static String oneLine(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    // showDetails shows the NFT's description, or else its token's, and its attributes, but none
    // of a look-alike's, which are made to mislead.
    private void showDetails(TokenMeta meta, String commitment) {
        boolean lookAlike = meta != null && meta.lookAlike;
        TokenMeta.NftType type = meta == null || lookAlike ? null : meta.nftType(commitment);
        String text = type != null && !type.description.isEmpty() ? type.description
                : meta != null && meta.hasMetadata && !lookAlike ? meta.description : "";
        TextView description = findViewById(R.id.nftDescription);
        description.setText(text);
        int shown = text.isEmpty() ? View.GONE : View.VISIBLE;
        description.setVisibility(shown);
        findViewById(R.id.nftDescriptionLabel).setVisibility(shown);

        LinearLayout attributes = findViewById(R.id.nftAttributes);
        attributes.removeAllViews();
        Map<String, String> traits = type == null ? Collections.emptyMap() : type.attributes;
        for (Map.Entry<String, String> trait : traits.entrySet()) {
            View row = LayoutInflater.from(this).inflate(R.layout.nft_attribute_row, attributes, false);
            ((TextView) row.findViewById(R.id.nftAttributeName)).setText(oneLine(trait.getKey()));
            ((TextView) row.findViewById(R.id.nftAttributeValue)).setText(oneLine(trait.getValue()));
            attributes.addView(row);
        }
        shown = traits.isEmpty() ? View.GONE : View.VISIBLE;
        attributes.setVisibility(shown);
        findViewById(R.id.nftAttributesLabel).setVisibility(shown);
    }

    // showReceived shows when the NFT came, which opens the transaction it came in, if the main
    // screen has that transaction.
    private void showReceived() {
        // Off the main thread, as a wallet with many transactions takes a while to read.
        Context app = getApplicationContext();
        Network network = this.network;
        WeakReference<NftActivity> ref = new WeakReference<>(this);
        STORE_READER.execute(() -> {
            List<TransactionData> saved = new TransactionStore(app, network).getData();
            NftActivity screen = ref.get();
            if (screen == null) {
                return;
            }
            screen.runOnUiThread(() -> {
                if (!screen.isDestroyed()) {
                    screen.showReceived(saved);
                }
            });
        });
    }

    private void showReceived(List<TransactionData> saved) {
        TextView received = findViewById(R.id.nftReceived);
        for (TransactionData tx : saved) {
            if (tx.getTxid().equals(txid)) {
                // In the phone's language and its 12 or 24 hour clock.
                received.setText(getString(R.string.nft_received, DateUtils.formatDateTime(this,
                        tx.getTimestamp() * 1000, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME
                                | DateUtils.FORMAT_ABBREV_MONTH)));
                received.setOnClickListener(v -> detailsPopup = TransactionDetailsPopup.show(this, layout, tx,
                        settings.getLastBlockHeight(), network));
                received.setVisibility(View.VISIBLE);
                return;
            }
        }
        received.setVisibility(View.GONE);
    }

    private void copyCommitment(String commitment) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.source_text), commitment));
            Snackbar.make(layout, R.string.commitment_copied, Snackbar.LENGTH_LONG).show();
        }
    }
}
