package cash.bchd.android_neutrino.wallet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Locale;

import walletrpc.Api;

public class TokenMetaTest {

    private Locale locale;

    @Before
    public void setLocale() {
        locale = Locale.getDefault();
        Locale.setDefault(Locale.US);
    }

    @After
    public void restoreLocale() {
        Locale.setDefault(locale);
    }

    private static TokenMeta token(int decimals, boolean hasMetadata) {
        return new TokenMeta(Api.TokenMetadataResponse.Token.newBuilder().setCategory("aa")
                .setHasMetadata(hasMetadata).setName("Name").setSymbol("SYM").setDecimals(decimals)
                .setStatus(Api.TokenMetadataResponse.Status.VERIFIED).build());
    }

    @Test
    public void amountsUseDecimalsAndSymbol() {
        assertEquals("123.45 SYM", token(2, true).formatAmount(12345));
        assertEquals("0.01 SYM", token(2, true).formatAmount(1));
        assertEquals("1 SYM", token(2, true).formatAmount(100));
        assertEquals("1,000 SYM", token(0, true).formatAmount(1000));
    }

    @Test
    public void amountWithoutMetadataIsPlainUnits() {
        assertEquals("12,345", token(2, false).formatAmount(12345));
    }

    @Test
    public void largestAmountStaysExact() {
        assertEquals("18,446,744,073,709,551,615", token(0, false).formatAmount(-1L));
        assertEquals("184,467,440,737.09551615 SYM", token(8, true).formatAmount(-1L));
    }

    @Test
    public void negativeChanges() {
        assertEquals("-1.5 SYM", token(2, true).formatAmount(BigInteger.valueOf(-150)));
    }

    @Test
    public void eighteenDecimalsKeepEveryDigit() {
        assertEquals("0.000000000000000001", TokenMeta.formatUnits(BigInteger.ONE, 18));
    }

    @Test
    public void statusAndTrust() {
        TokenMeta verified = token(0, true);
        assertEquals(TokenMeta.Status.VERIFIED, verified.status);
        assertTrue(verified.verified());
        TokenMeta searching = new TokenMeta(Api.TokenMetadataResponse.Token.newBuilder()
                .setCategory("aa").build());
        assertEquals(TokenMeta.Status.SEARCHING, searching.status);
        assertFalse(searching.verified());
        TokenMeta known = new TokenMeta(Api.TokenMetadataResponse.Token.newBuilder()
                .setCategory("aa").setKnown(true).build());
        assertTrue(known.verified());
    }

    @Test
    public void nftTypesAreFoundByCommitment() {
        List<TokenMeta> tokens = TokenMeta.of(Api.TokenMetadataResponse.newBuilder().addTokens(
                Api.TokenMetadataResponse.Token.newBuilder().setCategory("aa").addNftTypes(
                        Api.TokenMetadataResponse.NftType.newBuilder().setCommitment("01").setName("One")))
                .build());
        assertEquals("One", tokens.get(0).nftType("01").name);
        assertNull(tokens.get(0).nftType("02"));
    }

    @Test
    public void attributesComeInTheOrderOfTheirNames() {
        TokenMeta token = new TokenMeta(Api.TokenMetadataResponse.Token.newBuilder().setCategory("c").setHasMetadata(true)
                .addNftTypes(Api.TokenMetadataResponse.NftType.newBuilder().setCommitment("01")
                        .putAttributes("Eyes", "Red").putAttributes("Background", "Blue")).build());
        assertEquals("[Background, Eyes]", token.nftType("01").attributes.keySet().toString());
    }

    @Test
    public void namesAndSymbolsThatCopy() {
        Api.TokenMetadataResponse.Token.Builder said = Api.TokenMetadataResponse.Token.newBuilder()
                .setCategory("aa").setHasMetadata(true).setName("Name").setSymbol("SYM")
                .setLookAlike(true).setReservedName(true).setKnownSymbol(true);
        TokenMeta token = new TokenMeta(said.build());
        assertTrue(token.lookAlike);
        assertTrue(token.reservedName);
        assertTrue(token.knownSymbol);
        assertFalse(token.reservedSymbol);

        // A copy that keeps the NFT types keeps them too.
        TokenMeta before = new TokenMeta(said.clone().addNftTypes(
                Api.TokenMetadataResponse.NftType.newBuilder().setCommitment("01")).build());
        TokenMeta copied = token.keepingNftTypes(before);
        assertEquals(1, copied.nftTypes.size());
        assertTrue(copied.reservedName && copied.knownSymbol && !copied.reservedSymbol);
    }
}
