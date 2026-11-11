package cash.bchd.android_neutrino.tokens;

import android.graphics.Typeface;
import android.widget.TextView;

import androidx.annotation.StringRes;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import cash.bchd.android_neutrino.R;
import cash.bchd.android_neutrino.wallet.TokenBalance;
import cash.bchd.android_neutrino.wallet.TokenMeta;

/**
 * TokenLabels holds what the screens show of each token, from what the wallet last said, so that a
 * token reads the same everywhere. A token with nothing to show keeps its short ID and its amount
 * in smallest units.
 */
public final class TokenLabels {

    private static final Map<String, TokenMeta> TOKENS = new ConcurrentHashMap<>();
    // The tokens the wallet told of in this run, as what was kept from the last one only stands
    // in until it does.
    private static final Set<String> SAID = ConcurrentHashMap.newKeySet();
    // Counts the changes, for a screen to tell that it drew older labels.
    private static final AtomicLong VERSION = new AtomicLong();
    // Told when the labels change, to keep them.
    private static volatile Runnable onChange;
    // Told when what was kept from the last run is loaded, which is after the first screen shows.
    private static volatile Runnable onLoad;
    // The hidden tokens, whose labels stay as they were, as the wallet forgets them.
    private static volatile Set<String> held = Collections.emptySet();

    private TokenLabels() {
    }

    /**
     * update takes what the wallet said, without NFT types, and returns whether anything
     * changed.  The types a token had stay, as the answer didn't ask for them.
     */
    public static boolean update(List<TokenMeta> tokens) {
        return update(tokens, false);
    }

    /**
     * update takes what the wallet said, and returns whether anything changed.  With
     * typesAsked, the answer's NFT types are the token's, none too, as when its registry
     * dropped them, but for none of a registry that has some, which the wallet is reading
     * again, and else the types it had stay.
     */
    public static boolean update(List<TokenMeta> tokens, boolean typesAsked) {
        AtomicBoolean changed = new AtomicBoolean();
        Set<String> held = TokenLabels.held;
        for (TokenMeta token : tokens) {
            if (held.contains(token.category) && TOKENS.containsKey(token.category)) {
                continue;
            }
            SAID.add(token.category);
            TOKENS.compute(token.category, (category, before) -> {
                boolean whole = typesAsked && (!token.nftTypes.isEmpty() || token.nftTypeCount == 0);
                TokenMeta now = whole ? token : token.keepingNftTypes(before);
                if (!now.equals(before)) {
                    changed.set(true);
                    VERSION.incrementAndGet();
                    // What shows of its images can change with what the wallet says of it.
                    TokenIcons.forgetDrawn(category);
                }
                return now;
            });
        }
        Runnable told = onChange;
        if (changed.get() && told != null) {
            told.run();
        }
        return changed.get();
    }

    /**
     * hold keeps the labels of the hidden tokens as they are, as the wallet forgets what it knew of
     * them, so that Hidden Tokens can still name them. A token that's shown again is told of anew,
     * and lets go of its NFT types, which the wallet forgot, so that its screens ask for them.
     */
    public static void hold(Set<String> hidden) {
        Set<String> before = held;
        held = Collections.unmodifiableSet(new HashSet<>(hidden));
        for (String category : before) {
            if (!hidden.contains(category)) {
                TOKENS.computeIfPresent(category, (key, token) -> token.withoutNftTypes());
            }
        }
    }

    /**
     * hidden returns whether a token is one the user hid.
     */
    public static boolean hidden(String category) {
        return held.contains(category);
    }

    /**
     * load takes what was kept from the last run, under anything the wallet has said since.
     */
    public static void load(List<TokenMeta> tokens) {
        boolean loaded = false;
        for (TokenMeta token : tokens) {
            if (TOKENS.putIfAbsent(token.category, token) == null) {
                VERSION.incrementAndGet();
                loaded = true;
                // What was drawn before, with no label, can be what a look-alike's
                // label has it not show.
                TokenIcons.forgetDrawn(token.category);
            }
        }
        Runnable told = onLoad;
        if (loaded && told != null) {
            told.run();
        }
    }

    /**
     * onLoad has the listener told when what was kept from the last run is loaded, on no
     * particular thread, or no one with null.
     */
    public static synchronized void onLoad(Runnable listener) {
        onLoad = listener;
    }

    /**
     * stopOnLoad has no one told any more if the listener is the one told, and leaves another
     * that took its place, as a screen opened on top of another took it, and goes first.
     */
    public static synchronized void stopOnLoad(Runnable listener) {
        if (onLoad == listener) {
            onLoad = null;
        }
    }

    /**
     * saidThisRun returns whether the wallet told of the token since the app started, whatever
     * was kept of it from before.
     */
    public static boolean saidThisRun(String category) {
        return SAID.contains(category);
    }

    /**
     * all returns everything the wallet has said, to keep.
     */
    public static List<TokenMeta> all() {
        return new ArrayList<>(TOKENS.values());
    }

    /**
     * onChange has the listener told when the labels change, on no particular thread.
     */
    public static void onChange(Runnable listener) {
        onChange = listener;
    }

    /**
     * version returns what changes when the labels do, whichever screen asked.
     */
    public static long version() {
        return VERSION.get();
    }

    /**
     * get returns what the wallet said of a token, or null.
     */
    public static TokenMeta get(String category) {
        return TOKENS.get(category);
    }

    /**
     * clear forgets everything, as switching networks does.
     */
    public static void clear() {
        TOKENS.clear();
        SAID.clear();
    }

    /**
     * title returns what to call a token: its name, or its short ID.
     */
    public static String title(String category) {
        TokenMeta token = TOKENS.get(category);
        return token != null && token.hasMetadata ? token.name : TokenBalance.shortId(category);
    }

    /**
     * amount shows a fungible amount of a token, which is a uint64 as the API has it.
     */
    public static String amount(String category, long amount) {
        return amount(category, new BigInteger(Long.toUnsignedString(amount)));
    }

    /**
     * amount shows a number of the token's smallest units, which is negative for a change: with
     * the token's symbol, unless its name or symbol copies another's, as an amount of it is made
     * to read as an amount of something else.
     */
    public static String amount(String category, BigInteger amount) {
        TokenMeta token = TOKENS.get(category);
        if (token != null && (token.lookAlike || token.reservedSymbol || token.knownSymbol)) {
            return number(category, amount);
        }
        return token != null ? token.formatAmount(amount) : TokenMeta.formatUnits(amount, 0);
    }

    /**
     * number shows a number of the token's smallest units with its decimals, but not its symbol,
     * for where the token is plain, as on its own screen.
     */
    public static String number(String category, BigInteger amount) {
        TokenMeta token = TOKENS.get(category);
        return TokenMeta.formatUnits(amount, token != null && token.hasMetadata ? token.decimals : 0);
    }

    /**
     * isLookAlike returns whether a token has the name or symbol of another.
     */
    public static boolean isLookAlike(String category) {
        TokenMeta token = TOKENS.get(category);
        return token != null && token.lookAlike;
    }

    /**
     * trusted returns whether what a token's registry says can show where no badge says how far to
     * trust it, as in a transaction's row: it's verified, and isn't a look-alike, whose name is
     * made to mislead.
     */
    public static boolean trusted(String category) {
        TokenMeta token = TOKENS.get(category);
        return token != null && token.hasMetadata && token.verified() && !token.lookAlike;
    }

    /**
     * listTitle returns what to call a token where no badge shows: its name only if it's trusted.
     */
    public static String listTitle(String category) {
        return trusted(category) ? TOKENS.get(category).name : TokenBalance.shortId(category);
    }

    /**
     * listAmount shows an amount where no badge shows: with its symbol only if the token is
     * trusted, and its decimals either way.
     */
    public static String listAmount(String category, BigInteger amount) {
        return trusted(category) ? amount(category, amount) : number(category, amount);
    }

    /**
     * listSymbol returns the token's symbol where no badge shows, or null: only if the token is
     * trusted and its amounts show it, as a symbol that copies another's doesn't.
     */
    public static String listSymbol(String category) {
        if (!trusted(category)) {
            return null;
        }
        TokenMeta token = TOKENS.get(category);
        return token.reservedSymbol || token.knownSymbol || token.symbol.isEmpty() ? null : token.symbol;
    }

    /**
     * showTitle shows what to call a token: a name in the screen's font, or a short ID in monospace,
     * as its digits are all there is to tell it by. A bold one stays bold.
     */
    public static void showTitle(TextView view, String category, String title, boolean bold) {
        boolean shortId = title.equals(TokenBalance.shortId(category));
        view.setTypeface(shortId ? Typeface.MONOSPACE : Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        view.setText(title);
    }

    /**
     * nftName returns what the registry calls an NFT, or null, as for a look-alike, whose NFTs'
     * names are made to mislead as much as its own.
     */
    public static String nftName(String category, String commitment) {
        TokenMeta token = TOKENS.get(category);
        TokenMeta.NftType type = token == null || token.lookAlike ? null : token.nftType(commitment);
        return type == null ? null : type.name;
    }

    /**
     * listNftName returns what to call an NFT where no badge shows, as in a transaction's details:
     * its name only if it can be trusted, as with listTitle, or else null.
     */
    public static String listNftName(String category, String commitment) {
        TokenMeta token = TOKENS.get(category);
        return token != null && token.verified() && !token.lookAlike ? nftName(category, commitment) : null;
    }

    /**
     * hasNftTypes returns whether the wallet said what the token's NFTs are called.
     */
    public static boolean hasNftTypes(String category) {
        TokenMeta token = TOKENS.get(category);
        return token != null && !token.nftTypes.isEmpty();
    }

    /**
     * badge returns the string that says how far to trust what shows of a token, or 0 for none:
     * a look-alike comes first, as it's the warning that matters, then what went wrong.
     */
    @StringRes
    public static int badge(String category) {
        TokenMeta token = TOKENS.get(category);
        if (token == null) {
            return 0;
        }
        if (token.lookAlike) {
            return token.reservedName ? R.string.token_reserved_name : R.string.token_look_alike;
        }
        if (token.reservedSymbol) {
            return R.string.token_reserved_symbol;
        }
        if (token.knownSymbol) {
            return R.string.token_known_symbol;
        }
        if (token.status == TokenMeta.Status.FAILED) {
            return R.string.token_identity_not_found;
        }
        if (token.status == TokenMeta.Status.UNAVAILABLE) {
            return R.string.token_node_needs_index;
        }
        if (token.registryFailed) {
            return R.string.token_registry_failed;
        }
        if (!token.hasMetadata) {
            return 0;
        }
        if (token.known) {
            return R.string.token_known;
        }
        return token.verified() ? R.string.token_verified : R.string.token_not_yet_verified;
    }
}
