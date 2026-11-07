package cash.bchd.android_neutrino.wallet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

import cash.bchd.android_neutrino.tokens.TokenLabels;
import walletrpc.Api;

public class TokenLabelFileTest {

    private static final String CATEGORY = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Before
    public void forget() {
        TokenLabels.clear();
    }

    private static TokenMeta token(String name) {
        return new TokenMeta(Api.TokenMetadataResponse.Token.newBuilder().setCategory(CATEGORY)
                .setHasMetadata(true).setName(name).setSymbol("SYM").setDecimals(2).setLookAlike(true)
                .setStatus(Api.TokenMetadataResponse.Status.VERIFIED)
                .addNftTypes(Api.TokenMetadataResponse.NftType.newBuilder().setCommitment("01").setName("One"))
                .build());
    }

    @Test
    public void keepsWhatAScreenNeedsButNotNftTypes() throws IOException {
        File file = folder.newFile();
        TokenLabelFile.write(file, Collections.singletonList(token("Name")));
        List<TokenMeta> read = TokenLabelFile.read(file);
        assertEquals(1, read.size());
        TokenMeta token = read.get(0);
        assertEquals(CATEGORY, token.category);
        assertEquals("Name", token.name);
        assertEquals("SYM", token.symbol);
        assertEquals(2, token.decimals);
        assertTrue(token.lookAlike);
        assertEquals(TokenMeta.Status.VERIFIED, token.status);
        assertTrue(token.nftTypes.isEmpty());
    }

    @Test
    public void aCopyWithNftTypesIsKeptWithoutThem() throws IOException {
        TokenMeta plain = new TokenMeta(token("Name").kept());
        TokenMeta copy = plain.keepingNftTypes(token("Name"));
        assertFalse(copy.nftTypes.isEmpty());
        File file = folder.newFile();
        TokenLabelFile.write(file, Collections.singletonList(copy));
        assertTrue(TokenLabelFile.read(file).get(0).nftTypes.isEmpty());
    }

    @Test
    public void aFileThatCantBeReadLeavesNothing() throws IOException {
        File file = folder.newFile();
        Files.write(file.toPath(), new byte[]{(byte) 0xff, (byte) 0xff, (byte) 0xff, 1, 2, 3});
        assertTrue(TokenLabelFile.read(file).isEmpty());
        assertTrue(TokenLabelFile.read(new File(folder.getRoot(), "missing")).isEmpty());
    }

    @Test
    public void aFileTooLargeToReadAtStartGoes() throws IOException {
        File file = folder.newFile();
        Files.write(file.toPath(), new byte[(4 << 20) + 1]);
        assertTrue(TokenLabelFile.read(file).isEmpty());
        assertFalse(file.exists());
    }

    @Test
    public void whatWasKeptDoesntCountAsSaidThisRun() {
        TokenLabels.load(Collections.singletonList(token("Kept")));
        assertFalse(TokenLabels.saidThisRun(CATEGORY));
        TokenLabels.update(Collections.singletonList(token("Said")));
        assertTrue(TokenLabels.saidThisRun(CATEGORY));
        TokenLabels.clear();
        assertFalse(TokenLabels.saidThisRun(CATEGORY));
    }

    @Test
    public void aChangedDescriptionIsAChange() {
        Api.TokenMetadataResponse.Token.Builder token = Api.TokenMetadataResponse.Token.newBuilder()
                .setCategory(CATEGORY).setHasMetadata(true).setName("Name").setDescription("Old");
        TokenLabels.update(Collections.singletonList(new TokenMeta(token.build())));
        assertTrue(TokenLabels.update(Collections.singletonList(new TokenMeta(token.setDescription("New").build()))));
        assertEquals("New", TokenLabels.get(CATEGORY).description);
    }

    @Test
    public void writingLeavesNoTemporaryFile() throws IOException {
        File file = new File(folder.getRoot(), "labels");
        TokenLabelFile.write(file, Collections.singletonList(token("Name")));
        TokenLabelFile.write(file, Collections.singletonList(token("Newer")));
        assertEquals("Newer", TokenLabelFile.read(file).get(0).name);
        assertFalse(new File(file.getPath() + ".tmp").exists());
    }

    @Test
    public void eachNetworkHasItsOwnFile() {
        assertNotEquals(TokenLabelFile.fileName(Network.MAINNET), TokenLabelFile.fileName(Network.TESTNET4));
    }

    @Test
    public void whatTheWalletSaidComesBeforeWhatWasKept() {
        TokenLabels.update(Collections.singletonList(token("Said")));
        TokenLabels.load(Collections.singletonList(token("Kept")));
        assertEquals("Said", TokenLabels.get(CATEGORY).name);
    }

    @Test
    public void whatWasKeptGivesWayToWhatTheWalletSays() {
        TokenLabels.load(Collections.singletonList(token("Kept")));
        assertEquals("Kept", TokenLabels.get(CATEGORY).name);
        TokenLabels.update(Collections.singletonList(token("Said")));
        assertEquals("Said", TokenLabels.get(CATEGORY).name);
    }

    @Test
    public void loadingTellsScreensToDrawAgainButSavesNothing() {
        boolean[] told = new boolean[1];
        TokenLabels.onChange(() -> told[0] = true);
        long before = TokenLabels.version();
        TokenLabels.load(Collections.singletonList(token("Kept")));
        TokenLabels.onChange(null);
        assertNotEquals(before, TokenLabels.version());
        assertFalse(told[0]);
    }

    @Test
    public void aChangeIsTold() {
        int[] told = new int[1];
        TokenLabels.onChange(() -> told[0]++);
        TokenLabels.update(Collections.singletonList(token("Said")));
        TokenLabels.update(Collections.singletonList(token("Said")));
        TokenLabels.onChange(null);
        assertEquals(1, told[0]);
    }
}
