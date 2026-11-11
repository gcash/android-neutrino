package cash.bchd.android_neutrino.tokens;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cash.bchd.android_neutrino.wallet.TokenMeta;
import cash.bchd.android_neutrino.wallet.Wallet;

/**
 * NftArt loads the art of NFTs that a screen shows, the ones shown last first, asking the wallet
 * for all of it at once, as the wallet's lines take turns at each gateway. Only a token whose
 * images are to show has any loaded, as the art's host sees who asks. It's for the main thread,
 * and tells it what it loaded. A screen stops it when it goes, which only stops the telling: what
 * was asked for still loads and is saved.
 */
public final class NftArt {

    /**
     * Loaded is told of the art that was saved.
     */
    public interface Loaded {
        void loaded(String category, String commitment);
    }

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    // Reads whether art is saved already, off the main thread, as it reads the art's files.
    private static final ExecutorService CHECKER = Executors.newSingleThreadExecutor();
    private final Context context;
    // Let go of once stopped, as it holds its screen, and a download can take any time to end.
    private Loaded done;
    // What was asked for, which isn't asked for again, what failed of that, and what's waiting its
    // turn.
    private final Set<String> asked = new HashSet<>();
    private final Set<String> failed = new HashSet<>();
    private final Deque<String[]> waiting = new ArrayDeque<>();
    // The tokens whose images the user asked for, whose art goes ahead of what loads by itself.
    private final Set<String> byUser = new HashSet<>();
    // The art found saved from the URI the registry names, by token, NFT and URI, so that each
    // row that shows it isn't checked again.
    private final TokenIcons.SavedChecks savedChecked = new TokenIcons.SavedChecks();
    private boolean stopped;

    public NftArt(Context context, Loaded done) {
        // The application's, as a download holds this until it ends, which can be long after the
        // screen that asked is gone.
        this.context = context.getApplicationContext();
        this.done = done;
    }

    /**
     * request has the art of an NFT that shows load, if it's to show and isn't there yet.
     */
    public void request(String category, String commitment) {
        // A look-alike's art wouldn't show, so nothing should reach its host. Art saved from
        // another URI than the registry names now is loaded again.
        String uri = uri(category, commitment);
        if (!TokenIcons.showing(category) || uri == null
                || savedChecked.has(key(category, commitment) + " " + uri, TokenIcons.forgotten())
                || !asked.add(key(category, commitment))) {
            return;
        }
        waiting.addFirst(new String[]{category, commitment, null});
        loadNext();
    }

    /**
     * requestIcon has a token's own icon load, in the same turns as NFTs' art.
     */
    public void requestIcon(String category, String uri) {
        // An icon saved from another URI than the registry names now is loaded again.
        if (savedChecked.has(category + " " + uri, TokenIcons.forgotten()) || !asked.add(category)) {
            return;
        }
        waiting.addFirst(new String[]{category, null, uri});
        loadNext();
    }

    /**
     * forUser has the art of a token load as the user's, as after a Load icon tap.
     */
    public void forUser(String category) {
        byUser.add(category);
    }

    /**
     * retry lets what failed be asked for again, as when the user asks for a token's images.
     */
    public void retry() {
        asked.removeAll(failed);
        failed.clear();
    }

    /**
     * stop stops telling the screen, as it's going. What it asked for still loads and is saved.
     */
    public void stop() {
        stopped = true;
        waiting.clear();
        done = null;
    }

    private static String key(String category, String commitment) {
        return commitment == null ? category : category + "-" + commitment;
    }

    private static String uri(String category, String commitment) {
        TokenMeta token = TokenLabels.get(category);
        TokenMeta.NftType type = token == null ? null : token.nftType(commitment);
        if (type == null) {
            return null;
        }
        // Some NFTs have only a full image.
        String art = type.iconUri.isEmpty() ? type.imageUri : type.iconUri;
        return art.isEmpty() ? null : art;
    }

    private void loadNext() {
        Wallet wallet = Wallet.getInstance();
        // All at once: the wallet's lines take turns at each gateway, and put what the user
        // asked for first.
        while (!stopped && !waiting.isEmpty()) {
            String[] next = waiting.pollFirst();
            String category = next[0];
            String commitment = next[1];
            // What was waiting may no longer be wanted, or known, and the token may have turned
            // out to be a look-alike, whose host nothing should reach. What can't load now can be
            // asked for again.
            String uri = commitment == null ? next[2] : uri(category, commitment);
            if (uri == null || !TokenIcons.showing(category) || wallet == null || !wallet.isRunning()) {
                asked.remove(key(category, commitment));
                continue;
            }
            boolean forUser = byUser.contains(category);
            CHECKER.execute(() -> loadUnlessSaved(wallet, category, commitment, uri, forUser));
        }
    }

    // loadUnlessSaved loads the art, off the main thread, unless it's saved from the URI already,
    // which the screen then knows, and checks no more.
    private void loadUnlessSaved(Wallet wallet, String category, String commitment, String uri, boolean forUser) {
        long forgotten = TokenIcons.forgotten();
        boolean there = commitment == null ? TokenIcons.iconSavedFrom(context, category, uri)
                : TokenIcons.hasNft(context, category, commitment) && TokenIcons.savedFrom(context, category, commitment, uri);
        if (there) {
            HANDLER.post(() -> {
                savedChecked.add(key(category, commitment) + " " + uri, forgotten);
                asked.remove(key(category, commitment));
            });
            return;
        }
        load(wallet.tokenIconAsync(uri, forUser), category, commitment, uri);
    }

    private void load(ListenableFuture<byte[]> data, String category, String commitment, String uri) {
        Futures.addCallback(data, new FutureCallback<byte[]>() {
            @Override
            public void onSuccess(byte[] image) {
                // Whatever a stranger's file does, its turn has to end.
                boolean saved = false;
                try {
                    saved = commitment == null ? TokenIcons.save(context, category, image, uri)
                            : TokenIcons.saveNft(context, category, commitment, image, uri);
                } catch (RuntimeException | OutOfMemoryError e) {
                    e.printStackTrace();
                }
                boolean done = saved;
                HANDLER.post(() -> finished(category, commitment, done));
            }

            @Override
            public void onFailure(Throwable t) {
                HANDLER.post(() -> finished(category, commitment, false));
            }
        }, MoreExecutors.directExecutor());
    }

    private void finished(String category, String commitment, boolean saved) {
        if (!stopped) {
            if (saved) {
                done.loaded(category, commitment);
            } else {
                failed.add(key(category, commitment));
            }
        }
    }
}
