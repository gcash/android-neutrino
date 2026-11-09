package cash.bchd.android_neutrino.wallet;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import walletrpc.Api;

/**
 * TokenMeta is what the wallet can say of a token from its BCMR registry or the Known list, and
 * how far it got with verifying that.
 */
public final class TokenMeta {

    // The longest symbol an amount shows.
    private static final int MAX_SHOWN_SYMBOL = 16;

    /**
     * Status is how far the wallet got with a token's identity.
     */
    public enum Status {
        // Not followed to the tip yet, so what shows isn't verified.
        SEARCHING,
        // Followed to the tip.
        VERIFIED,
        // Asks a full node that has no BCMR index.
        UNAVAILABLE,
        // Not where it had to be.
        FAILED
    }

    /**
     * NftType is what a registry says of an NFT by its commitment.
     */
    public static final class NftType {
        public final String commitment;
        public final String name;
        public final String description;
        public final String iconUri;
        // Its art in full, if the registry gives it.
        public final String imageUri;
        // Its traits, by name, in the order of their names.
        public final Map<String, String> attributes;

        NftType(Api.TokenMetadataResponse.NftType type) {
            commitment = type.getCommitment();
            name = type.getName();
            description = type.getDescription();
            iconUri = type.getIconUri();
            imageUri = type.getImageUri();
            attributes = Collections.unmodifiableMap(new TreeMap<>(type.getAttributesMap()));
        }
    }

    public final String category;
    // Whether the fields from name on hold anything.
    public final boolean hasMetadata;
    public final String name;
    public final String description;
    public final String symbol;
    public final int decimals;
    public final String iconUri;
    public final String webUri;
    public final Status status;
    public final boolean known;
    public final boolean loadsByItself;
    public final boolean lookAlike;
    public final boolean burned;
    // Whether the metadata is from a file other than the published one, so nothing vouches for it.
    public final boolean registryChanged;
    // Whether the token published a registry that nothing could be read from.
    public final boolean registryFailed;
    // Whether a look-alike is one for its name being the coin's own, not a Known token's.
    public final boolean reservedName;
    // Whether its symbol copies a reserved one, or a Known token's, so its amounts show none.
    public final boolean reservedSymbol;
    public final boolean knownSymbol;
    public final List<NftType> nftTypes;
    private final Map<String, NftType> nftTypesByCommitment;
    // What the wallet said, as it's kept between runs: without the NFT types, of which a
    // collection can have thousands, and which load with its screens.
    private final Api.TokenMetadataResponse.Token kept;

    TokenMeta(Api.TokenMetadataResponse.Token token) {
        kept = token.toBuilder().clearNftTypes().build();
        category = token.getCategory();
        hasMetadata = token.getHasMetadata();
        name = token.getName();
        description = token.getDescription();
        symbol = token.getSymbol();
        decimals = token.getDecimals();
        iconUri = token.getIconUri();
        webUri = token.getWebUri();
        status = statusOf(token.getStatus());
        known = token.getKnown();
        loadsByItself = token.getLoadsByItself();
        lookAlike = token.getLookAlike();
        burned = token.getBurned();
        registryChanged = token.getRegistryChanged();
        registryFailed = token.getRegistryFailed();
        reservedName = token.getReservedName();
        reservedSymbol = token.getReservedSymbol();
        knownSymbol = token.getKnownSymbol();
        List<NftType> types = new ArrayList<>();
        for (Api.TokenMetadataResponse.NftType type : token.getNftTypesList()) {
            types.add(new NftType(type));
        }
        nftTypes = Collections.unmodifiableList(types);
        nftTypesByCommitment = byCommitment(nftTypes);
    }

    // A copy of the token with the NFT types.
    private TokenMeta(TokenMeta token, List<NftType> types) {
        kept = token.kept;
        category = token.category;
        hasMetadata = token.hasMetadata;
        name = token.name;
        description = token.description;
        symbol = token.symbol;
        decimals = token.decimals;
        iconUri = token.iconUri;
        webUri = token.webUri;
        status = token.status;
        known = token.known;
        loadsByItself = token.loadsByItself;
        lookAlike = token.lookAlike;
        burned = token.burned;
        registryChanged = token.registryChanged;
        registryFailed = token.registryFailed;
        reservedName = token.reservedName;
        reservedSymbol = token.reservedSymbol;
        knownSymbol = token.knownSymbol;
        nftTypes = types;
        nftTypesByCommitment = byCommitment(types);
    }

    Api.TokenMetadataResponse.Token kept() {
        return kept;
    }

    private static Map<String, NftType> byCommitment(List<NftType> types) {
        Map<String, NftType> map = new HashMap<>();
        for (NftType type : types) {
            map.put(type.commitment, type);
        }
        return map;
    }

    /**
     * keepingNftTypes returns the token with the NFT types of what was said of it before, if it
     * came without any, as an answer about many tokens does, and it's still the same token, as
     * far to be trusted.
     */
    public TokenMeta keepingNftTypes(TokenMeta before) {
        if (before == null || !nftTypes.isEmpty() || before.nftTypes.isEmpty() || !hasMetadata
                || !name.equals(before.name) || !symbol.equals(before.symbol)
                || verified() != before.verified() || registryChanged != before.registryChanged) {
            return this;
        }
        return new TokenMeta(this, before.nftTypes);
    }

    private static Status statusOf(Api.TokenMetadataResponse.Status status) {
        switch (status) {
            case VERIFIED:
                return Status.VERIFIED;
            case UNAVAILABLE:
                return Status.UNAVAILABLE;
            case FAILED:
                return Status.FAILED;
            default:
                return Status.SEARCHING;
        }
    }

    /**
     * of returns what the wallet answered, by category.
     */
    static List<TokenMeta> of(Api.TokenMetadataResponse response) {
        List<TokenMeta> tokens = new ArrayList<>();
        for (Api.TokenMetadataResponse.Token token : response.getTokensList()) {
            tokens.add(new TokenMeta(token));
        }
        return tokens;
    }

    /**
     * verified returns whether what shows is known good: from an identity followed to the tip,
     * which a collection still being published keeps adding to, or from the Known list.
     */
    public boolean verified() {
        return status == Status.VERIFIED || known;
    }

    /**
     * nftType returns what the registry says of the NFT with the commitment, or null.
     */
    public NftType nftType(String commitment) {
        return nftTypesByCommitment.get(commitment);
    }

    /**
     * formatAmount shows a fungible amount as the token's own: its smallest units moved past the
     * decimals, then its symbol. The amount is a uint64, as the API has it.
     */
    public String formatAmount(long amount) {
        return formatAmount(new BigInteger(Long.toUnsignedString(amount)));
    }

    /**
     * formatAmount shows an amount of smallest units, which can be negative for a change.
     */
    public String formatAmount(BigInteger amount) {
        return formatUnits(amount, hasMetadata ? decimals : 0)
                + (hasMetadata ? " " + shownSymbol() : "");
    }

    // shownSymbol returns the base symbol, up to the first hyphen, as the CHIP has amounts show it,
    // and no more of it than leaves room for what else a row shows.
    private String shownSymbol() {
        int hyphen = symbol.indexOf('-');
        String base = hyphen > 0 ? symbol.substring(0, hyphen) : symbol;
        return base.length() <= MAX_SHOWN_SYMBOL ? base : base.substring(0, MAX_SHOWN_SYMBOL) + "…";
    }

    /**
     * formatUnits shows an amount of smallest units with the decimals, grouped as the locale
     * groups numbers, with no digits it doesn't need.
     */
    public static String formatUnits(BigInteger amount, int decimals) {
        BigDecimal value = new BigDecimal(amount, decimals);
        NumberFormat format = NumberFormat.getNumberInstance();
        format.setMaximumFractionDigits(decimals);
        format.setMinimumFractionDigits(0);
        return format.format(value);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof TokenMeta)) {
            return false;
        }
        TokenMeta o = (TokenMeta) other;
        return kept.equals(o.kept) && nftTypes.size() == o.nftTypes.size();
    }

    @Override
    public int hashCode() {
        return Objects.hash(category, name, symbol, decimals, status);
    }
}
