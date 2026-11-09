package cash.bchd.android_neutrino.wallet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Collections;

import cash.bchd.android_neutrino.R;
import cash.bchd.android_neutrino.tokens.TokenLabels;
import walletrpc.Api;

public class TokenLabelsTest {

    private static final String CATEGORY = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

    @Before
    public void forget() {
        TokenLabels.clear();
    }

    private static Api.TokenMetadataResponse.Token.Builder token() {
        return Api.TokenMetadataResponse.Token.newBuilder().setCategory(CATEGORY).setHasMetadata(true)
                .setName("Name").setSymbol("SYM").setDecimals(2);
    }

    private static boolean update(Api.TokenMetadataResponse.Token.Builder token) {
        return TokenLabels.update(Collections.singletonList(new TokenMeta(token.build())));
    }

    @Test
    public void aTokenWithNothingKeepsItsShortIdAndRawAmount() {
        assertEquals(TokenBalance.shortId(CATEGORY), TokenLabels.title(CATEGORY));
        assertEquals("1,000", TokenLabels.amount(CATEGORY, 1000));
        assertEquals(0, TokenLabels.badge(CATEGORY));
    }

    @Test
    public void updateSaysWhenSomethingChanged() {
        assertTrue(update(token()));
        assertFalse(update(token()));
        assertTrue(update(token().setStatus(Api.TokenMetadataResponse.Status.VERIFIED)));
    }

    @Test
    public void unverifiedShowsButSaysSo() {
        update(token());
        assertEquals("Name", TokenLabels.title(CATEGORY));
        assertEquals("10 SYM", TokenLabels.amount(CATEGORY, 1000));
        assertEquals(R.string.token_not_yet_verified, TokenLabels.badge(CATEGORY));
    }

    @Test
    public void whereNoBadgeShowsOnlyATrustedNameDoes() {
        update(token());
        assertEquals(TokenBalance.shortId(CATEGORY), TokenLabels.listTitle(CATEGORY));
        update(token().setStatus(Api.TokenMetadataResponse.Status.VERIFIED));
        assertEquals("Name", TokenLabels.listTitle(CATEGORY));
        update(token().setStatus(Api.TokenMetadataResponse.Status.VERIFIED).setLookAlike(true));
        assertEquals(TokenBalance.shortId(CATEGORY), TokenLabels.listTitle(CATEGORY));
    }

    @Test
    public void badgesPutTheWarningFirst() {
        update(token().setKnown(true).setLookAlike(true));
        assertEquals(R.string.token_look_alike, TokenLabels.badge(CATEGORY));
        update(token().setKnown(true).setStatus(Api.TokenMetadataResponse.Status.FAILED));
        assertEquals(R.string.token_identity_not_found, TokenLabels.badge(CATEGORY));
        update(token().setKnown(true));
        assertEquals(R.string.token_known, TokenLabels.badge(CATEGORY));
    }

    @Test
    public void problemsShowWithNoMetadata() {
        update(token().setHasMetadata(false).setStatus(Api.TokenMetadataResponse.Status.UNAVAILABLE));
        assertEquals(R.string.token_node_needs_index, TokenLabels.badge(CATEGORY));
    }

    @Test
    public void nftNamesComeFromTheTypes() {
        update(token().addNftTypes(Api.TokenMetadataResponse.NftType.newBuilder()
                .setCommitment("01").setName("One")));
        assertEquals("One", TokenLabels.nftName(CATEGORY, "01"));
        assertEquals(null, TokenLabels.nftName(CATEGORY, "02"));
    }

    @Test
    public void aTokenThatMovedOnWithoutPublishingIsVerified() {
        update(token().setStatus(Api.TokenMetadataResponse.Status.VERIFIED).setPublicationStale(true));
        assertEquals(R.string.token_verified, TokenLabels.badge(CATEGORY));
    }

    @Test
    public void aKnownTokensChangedFileIsKnown() {
        update(token().setKnown(true).setStatus(Api.TokenMetadataResponse.Status.VERIFIED));
        assertEquals(R.string.token_known, TokenLabels.badge(CATEGORY));
        assertEquals("Name", TokenLabels.listTitle(CATEGORY));
    }

    @Test
    public void aRegistryThatFailedSaysSo() {
        update(Api.TokenMetadataResponse.Token.newBuilder().setCategory(CATEGORY).setRegistryFailed(true));
        assertEquals(R.string.token_registry_failed, TokenLabels.badge(CATEGORY));
    }

    private static Api.TokenMetadataResponse.NftType nft() {
        return Api.TokenMetadataResponse.NftType.newBuilder().setCommitment("01").setName("One").build();
    }

    @Test
    public void anAnswerWithoutNftTypesKeepsThem() {
        update(token().addNftTypes(nft()));
        assertFalse(update(token()));
        assertEquals("One", TokenLabels.nftName(CATEGORY, "01"));
    }

    @Test
    public void anotherTokenNameDropsTheNftTypes() {
        update(token().addNftTypes(nft()));
        update(token().setName("Other"));
        assertEquals(null, TokenLabels.nftName(CATEGORY, "01"));
    }

    @Test
    public void listsNameAnNftOnlyIfItCanBeTrusted() {
        update(token().addNftTypes(nft()));
        assertEquals(null, TokenLabels.listNftName(CATEGORY, "01"));
        update(token().addNftTypes(nft()).setStatus(Api.TokenMetadataResponse.Status.VERIFIED));
        assertEquals("One", TokenLabels.listNftName(CATEGORY, "01"));
        update(token().addNftTypes(nft()).setStatus(Api.TokenMetadataResponse.Status.VERIFIED).setLookAlike(true));
        assertEquals(null, TokenLabels.listNftName(CATEGORY, "01"));
    }

    @Test
    public void aLookAlikesNftsGoByCommitment() {
        update(token().addNftTypes(nft()));
        assertEquals("One", TokenLabels.nftName(CATEGORY, "01"));
        update(token().addNftTypes(nft()).setLookAlike(true));
        assertEquals(null, TokenLabels.nftName(CATEGORY, "01"));
    }

    @Test
    public void namesFromAnIdentityNotYetFollowedAreNotKeptOnceItsVerified() {
        update(token().addNftTypes(nft()));
        update(token().setStatus(Api.TokenMetadataResponse.Status.VERIFIED));
        assertEquals(null, TokenLabels.listNftName(CATEGORY, "01"));
        assertFalse(TokenLabels.hasNftTypes(CATEGORY));
    }

    @Test
    public void amountsLeaveOutASymbolThatCopies() {
        update(token());
        assertEquals("15 SYM", TokenLabels.amount(CATEGORY, BigInteger.valueOf(1500)));
        update(token().setLookAlike(true));
        assertEquals("15", TokenLabels.amount(CATEGORY, BigInteger.valueOf(1500)));
        update(token().setReservedSymbol(true));
        assertEquals("15", TokenLabels.amount(CATEGORY, BigInteger.valueOf(1500)));
        update(token().setKnownSymbol(true));
        assertEquals("15", TokenLabels.amount(CATEGORY, BigInteger.valueOf(1500)));
    }

    @Test
    public void amountsShowTheBaseSymbolAndNoMoreThanFitsARow() {
        update(token().setSymbol("MUSD-A"));
        assertEquals("15 MUSD", TokenLabels.amount(CATEGORY, BigInteger.valueOf(1500)));
        update(token().setSymbol("ABCDEFGHIJKLMNOPQRSTUVWXYZ"));
        assertEquals("15 ABCDEFGHIJKLMNOP\u2026", TokenLabels.amount(CATEGORY, BigInteger.valueOf(1500)));
    }

    @Test
    public void aSymbolThatCopiesSaysSo() {
        update(token().setStatus(Api.TokenMetadataResponse.Status.VERIFIED).setReservedSymbol(true));
        assertEquals(R.string.token_reserved_symbol, TokenLabels.badge(CATEGORY));
        update(token().setStatus(Api.TokenMetadataResponse.Status.VERIFIED).setKnownSymbol(true));
        assertEquals(R.string.token_known_symbol, TokenLabels.badge(CATEGORY));
        update(token().setLookAlike(true).setKnownSymbol(true));
        assertEquals(R.string.token_look_alike, TokenLabels.badge(CATEGORY));
        assertEquals("Name", TokenLabels.title(CATEGORY));
    }

    @Test
    public void aReservedNameSaysSo() {
        update(token().setLookAlike(true).setReservedName(true).setReservedSymbol(true));
        assertEquals(R.string.token_reserved_name, TokenLabels.badge(CATEGORY));
    }
}
