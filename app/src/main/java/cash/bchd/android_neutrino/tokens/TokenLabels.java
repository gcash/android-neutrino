package cash.bchd.android_neutrino.tokens;

import android.graphics.Typeface;
import android.widget.TextView;

import androidx.annotation.StringRes;

import java.math.BigInteger;
import java.util.ArrayList;
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

    private TokenLabels() {
    }

    /**
     * update takes what the wallet said, and returns whether anything changed.
     */
    public static boolean update(List<TokenMeta> tokens) {
        AtomicBoolean changed = new AtomicBoolean();
        for (TokenMeta token : tokens) {
            SAID.add(token.category);
            TOKENS.compute(token.category, (category, before) -> {
                TokenMeta now = token.keepingNftTypes(before);
                if (!now.equals(before)) {
                    changed.set(true);
                    VERSION.incrementAndGet();
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
     * load takes what was kept from the last run, under anything the wallet has said since.
     */
    public static void load(List<TokenMeta> tokens) {
        for (TokenMeta token : tokens) {
            if (TOKENS.putIfAbsent(token.category, token) == null) {
                VERSION.incrementAndGet();
            }
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
     * listTitle returns what to call a token where no badge shows, as in a transaction's row:
     * the name only if it can be trusted, as a look-alike's name is made to mislead.
     */
    public static String listTitle(String category) {
        TokenMeta token = TOKENS.get(category);
        return token != null && token.hasMetadata && token.verified() && !token.lookAlike
                ? token.name : TokenBalance.shortId(category);
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
     * nftName returns what the registry calls an NFT, or null.
     */
    public static String nftName(String category, String commitment) {
        TokenMeta token = TOKENS.get(category);
        TokenMeta.NftType type = token == null ? null : token.nftType(commitment);
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
        if (token.registryChanged && token.status == TokenMeta.Status.VERIFIED) {
            return R.string.token_registry_changed;
        }
        return token.verified() ? R.string.token_verified : R.string.token_not_yet_verified;
    }
}
