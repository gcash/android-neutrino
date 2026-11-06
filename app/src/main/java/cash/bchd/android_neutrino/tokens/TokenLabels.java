package cash.bchd.android_neutrino.tokens;

import android.graphics.Typeface;
import android.widget.TextView;

import androidx.annotation.StringRes;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private TokenLabels() {
    }

    /**
     * update takes what the wallet said, and returns whether anything changed.
     */
    public static boolean update(List<TokenMeta> tokens) {
        AtomicBoolean changed = new AtomicBoolean();
        for (TokenMeta token : tokens) {
            TOKENS.compute(token.category, (category, before) -> {
                TokenMeta now = token.keepingNftTypes(before);
                if (!now.equals(before)) {
                    changed.set(true);
                }
                return now;
            });
        }
        return changed.get();
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
     * amount shows a number of the token's smallest units, which is negative for a change.
     */
    public static String amount(String category, BigInteger amount) {
        TokenMeta token = TOKENS.get(category);
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
     * listAmount shows an amount where no badge shows, as in a transaction's row: without the
     * symbol of a look-alike, as an amount of it is made to read as an amount of something else.
     */
    public static String listAmount(String category, BigInteger amount) {
        return isLookAlike(category) ? number(category, amount) : amount(category, amount);
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
            return token.reservedSymbol ? R.string.token_reserved_symbol : R.string.token_look_alike;
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
