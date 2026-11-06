package cash.bchd.android_neutrino.tokens;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import cash.bchd.android_neutrino.wallet.TokenMeta;
import cash.bchd.android_neutrino.wallet.Wallet;

/**
 * NftArt loads the art of NFTs that a screen shows, the ones shown last first, a few at a time
 * across all screens, as a collection's art is often on a public gateway and can be megabytes. Only
 * a token whose images are to show has any loaded, as the art's host sees who asks. It's for the
 * main thread, and tells it what it loaded. A screen has to stop its own when it goes.
 */
public final class NftArt {

    /**
     * Loaded is told of the art that was saved.
     */
    public interface Loaded {
        void loaded(String category, String commitment);
    }

    // How many images load at once.
    private static final int MAX_LOADING = 3;

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    // The screens' loaders, and how many images are loading for all of them.
    private static final Set<NftArt> ACTIVE = new LinkedHashSet<>();
    private static int loading;

    private final Context context;
    private final Loaded done;
    // What was asked for, which isn't asked for again, what failed of that, and what's waiting its
    // turn.
    private final Set<String> asked = new HashSet<>();
    private final Set<String> failed = new HashSet<>();
    private final Deque<String[]> waiting = new ArrayDeque<>();
    private boolean stopped;

    public NftArt(Context context, Loaded done) {
        this.context = context;
        this.done = done;
        ACTIVE.add(this);
    }

    /**
     * request has the art of an NFT that shows load, if it's to show and isn't there yet.
     */
    public void request(String category, String commitment) {
        if (!TokenIcons.showing(category) || uri(category, commitment) == null
                || TokenIcons.hasNft(context, category, commitment)
                || !asked.add(category + "-" + commitment)) {
            return;
        }
        waiting.addFirst(new String[]{category, commitment});
        loadNext();
    }

    /**
     * retry lets what failed be asked for again, as when the user asks for a token's images.
     */
    public void retry() {
        asked.removeAll(failed);
        failed.clear();
    }

    /**
     * stop ends the loading, as the screen is going.
     */
    public void stop() {
        stopped = true;
        waiting.clear();
        ACTIVE.remove(this);
    }

    private static String uri(String category, String commitment) {
        TokenMeta token = TokenLabels.get(category);
        TokenMeta.NftType type = token == null ? null : token.nftType(commitment);
        return type == null || type.iconUri.isEmpty() ? null : type.iconUri;
    }

    private void loadNext() {
        Wallet wallet = Wallet.getInstance();
        while (!stopped && loading < MAX_LOADING && !waiting.isEmpty()) {
            String[] nft = waiting.pollFirst();
            // What was waiting may no longer be wanted, or known.
            String uri = uri(nft[0], nft[1]);
            if (uri == null || !TokenIcons.showing(nft[0]) || wallet == null || !wallet.isRunning()) {
                continue;
            }
            load(wallet.tokenIconAsync(uri), nft[0], nft[1]);
        }
    }

    private void load(ListenableFuture<byte[]> data, String category, String commitment) {
        loading++;
        Futures.addCallback(data, new FutureCallback<byte[]>() {
            @Override
            public void onSuccess(byte[] image) {
                boolean saved = TokenIcons.saveNft(context, category, commitment, image);
                HANDLER.post(() -> finished(category, commitment, saved));
            }

            @Override
            public void onFailure(Throwable t) {
                HANDLER.post(() -> finished(category, commitment, false));
            }
        }, MoreExecutors.directExecutor());
    }

    private void finished(String category, String commitment, boolean saved) {
        loading--;
        if (!stopped) {
            if (saved) {
                done.loaded(category, commitment);
            } else {
                failed.add(category + "-" + commitment);
            }
        }
        for (NftArt art : new ArrayList<>(ACTIVE)) {
            art.loadNext();
        }
    }
}
