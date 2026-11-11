package cash.bchd.android_neutrino.tokens;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class TokenIconsTest {

    @Test
    public void imagesPastTheLimitAreRefused() {
        assertTrue(TokenIcons.usable(512, 512));
        assertTrue(TokenIcons.usable(4096, 4096));
        assertFalse(TokenIcons.usable(4097, 10));
        assertFalse(TokenIcons.usable(10, 100000));
        assertFalse(TokenIcons.usable(0, 10));
        assertFalse(TokenIcons.usable(-1, -1));
    }

    @Test
    public void largeStillsCanBeBroughtDownToFullSize() {
        assertTrue(TokenIcons.usableFull(5000, 5000));
        assertTrue(TokenIcons.usableFull(20000, 1000));
        assertFalse(TokenIcons.usableFull(6000, 6000));
        assertFalse(TokenIcons.usableFull(16384, 16384));
        assertFalse(TokenIcons.usableFull(100000, 100000));
        assertFalse(TokenIcons.usableFull(0, 10));
    }

    @Test
    public void imagesScaleDownWithoutGoingUnderTheSize() {
        assertEquals(1, TokenIcons.sampleSize(64, 64, 64));
        assertEquals(1, TokenIcons.sampleSize(100, 100, 64));
        assertEquals(2, TokenIcons.sampleSize(128, 128, 64));
        assertEquals(8, TokenIcons.sampleSize(1024, 512, 64));
    }

    @Test
    public void tellsArtThatMovesByItsUri() {
        assertTrue(TokenIcons.movingUri("ipfs://cid/18.mp4"));
        assertTrue(TokenIcons.movingUri("ipfs://cid/18.GIF"));
        assertFalse(TokenIcons.movingUri("ipfs://cid/18.png"));
    }

    @Test
    public void tellsWhatCanAnimateByItsStart() {
        assertTrue(TokenIcons.canAnimate("GIF89a".getBytes()));
        assertTrue(TokenIcons.canAnimate("GIF87a".getBytes()));
        assertTrue(TokenIcons.canAnimate(webp("VP8X", 0x02)));
        assertTrue(TokenIcons.canAnimate(webp("VP8X", 0x12)));
        assertFalse(TokenIcons.canAnimate(webp("VP8X", 0x10)));
        assertFalse(TokenIcons.canAnimate(webp("VP8 ", 0x02)));
        assertFalse(TokenIcons.canAnimate(Arrays.copyOf(webp("VP8X", 0x02), 20)));
        byte[] notRiff = webp("VP8X", 0x02);
        notRiff[0] = 'X';
        assertFalse(TokenIcons.canAnimate(notRiff));
        assertFalse(TokenIcons.canAnimate("\u0089PNG".getBytes()));
        assertFalse(TokenIcons.canAnimate(new byte[0]));
    }

    // webp returns the start of a WebP file with the chunk and the flags.
    private static byte[] webp(String chunk, int flags) {
        byte[] start = new byte[30];
        byte[] names = ("RIFF\0\0\0\0WEBP" + chunk).getBytes();
        System.arraycopy(names, 0, start, 0, names.length);
        start[20] = (byte) flags;
        return start;
    }

    @Test
    public void fitsAnImageInItsProportions() {
        assertEquals("[100, 50]", java.util.Arrays.toString(TokenIcons.fitted(400, 200, 100)));
        assertEquals("[25, 100]", java.util.Arrays.toString(TokenIcons.fitted(100, 400, 100)));
        assertEquals("[100, 100]", java.util.Arrays.toString(TokenIcons.fitted(300, 300, 100)));
        assertEquals("[1, 100]", java.util.Arrays.toString(TokenIcons.fitted(1, 4000, 100)));
    }

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void forgettingATokenRemovesOnlyItsFiles() throws IOException {
        String token = "aa".repeat(32), other = "bb".repeat(32);
        File icons = folder.newFolder("icons"), art = folder.newFolder("art");
        String[] mine = {token, token + "-01", token + "-01.source", token + "-01.motion"};
        for (String name : mine) {
            assertTrue(new File(icons, name).createNewFile());
        }
        assertTrue(new File(art, token + "-01-1f").createNewFile());
        assertTrue(new File(icons, other).createNewFile());
        assertTrue(new File(art, other + "-01-1f").createNewFile());
        TokenIcons.forget(java.util.Collections.singleton(token), icons, art);
        assertEquals(Arrays.asList(other), Arrays.asList(icons.list()));
        assertEquals(Arrays.asList(other + "-01-1f"), Arrays.asList(art.list()));
    }

    @Test
    public void forgettingTokensListsEachFolderOnce() throws IOException {
        String a = "aa".repeat(32), b = "bb".repeat(32), c = "cc".repeat(32);
        java.util.concurrent.atomic.AtomicInteger lists = new java.util.concurrent.atomic.AtomicInteger();
        File icons = new File(folder.newFolder("icons2").getPath()) {
            @Override
            public File[] listFiles(java.io.FilenameFilter filter) {
                lists.incrementAndGet();
                return super.listFiles(filter);
            }
        };
        for (String name : new String[]{a, a + "-01", b, b + "-02.source", c, "short"}) {
            assertTrue(new File(icons, name).createNewFile());
        }
        TokenIcons.forget(new java.util.HashSet<>(Arrays.asList(a, b)), icons);
        assertEquals(1, lists.get());
        String[] left = icons.list();
        Arrays.sort(left);
        assertEquals(Arrays.asList(c, "short"), Arrays.asList(left));
    }

    @Test
    public void trimmingLeavesFilesBeingWritten() throws IOException {
        File art = folder.newFolder("motion");
        File writing = new File(art, "motion123.tmp");
        assertTrue(writing.createNewFile());
        File old = new File(art, "old");
        java.nio.file.Files.write(old.toPath(), new byte[1 << 20]);
        assertTrue(old.setLastModified(System.currentTimeMillis() - 60_000));
        File kept = new File(art, "kept");
        java.nio.file.Files.write(kept.toPath(), new byte[40 << 20]);
        TokenIcons.trimMotion(art, kept);
        assertTrue(writing.exists());
        assertTrue(kept.exists());
        assertFalse(old.exists());
    }

    @Test
    public void namesOfNftsNeverMeet() {
        String token = "aa".repeat(32);
        String longCommitment = "ab".repeat(40);
        String hashed = TokenIcons.nftName(token, longCommitment);
        assertEquals(token + "-h" + TokenIcons.sha256(longCommitment), hashed);
        assertEquals(64, TokenIcons.sha256("x").length());
        // No commitment, which is hex, has the mark's letter.
        assertFalse(hashed.substring(token.length() + 1).matches("[0-9a-f]*"));
        assertEquals(token + "-01", TokenIcons.nftName(token, "01"));
    }

    @Test
    public void artThatMovesHasASmallerLimit() {
        assertTrue(TokenIcons.usableMotion(2048, 2048));
        assertFalse(TokenIcons.usableMotion(2049, 10));
        assertFalse(TokenIcons.usableMotion(10, 4096));
        assertFalse(TokenIcons.usableMotion(0, 10));
    }

    @Test
    public void tellsAnSvgFromOtherFiles() {
        java.nio.charset.Charset utf8 = java.nio.charset.StandardCharsets.UTF_8;
        assertTrue(TokenIcons.looksLikeSvg("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(utf8)));
        assertTrue(TokenIcons.looksLikeSvg("\n <?xml version=\"1.0\"?>\n<svg></svg>".getBytes(utf8)));
        assertTrue(TokenIcons.looksLikeSvg("\uFEFF<svg></svg>".getBytes(utf8)));
        assertFalse(TokenIcons.looksLikeSvg("<?xml version=\"1.0\"?><html></html>".getBytes(utf8)));
        assertFalse(TokenIcons.looksLikeSvg(new byte[]{(byte) 0x89, 'P', 'N', 'G'}));
        assertFalse(TokenIcons.looksLikeSvg(new byte[0]));
    }


    @Test
    public void onlyACheapSvgIsDrawn() {
        java.nio.charset.Charset utf8 = java.nio.charset.StandardCharsets.UTF_8;
        assertTrue(TokenIcons.cheapSvg("<svg><use href=\"#a\"/></svg>".getBytes(utf8)));
        assertFalse(TokenIcons.cheapSvg(new byte[(256 << 10) + 1]));
        assertFalse(TokenIcons.cheapSvg("<!DOCTYPE svg [<!ENTITY a \"aa\">]><svg/>".getBytes(utf8)));
        assertFalse(TokenIcons.cheapSvg("<svg><image href=\"data:image/png;base64,AA\"/></svg>".getBytes(utf8)));
        assertFalse(TokenIcons.cheapSvg(("<svg>" + "<use href=\"#a\"/>".repeat(65) + "</svg>").getBytes(utf8)));
        // As the parser reads them: uses with a namespace prefix, data: and an entity spelled with
        // character references or in another case.
        assertFalse(TokenIcons.cheapSvg(("<svg xmlns:s=\"http://www.w3.org/2000/svg\">"
                + "<s:use href=\"#a\"/>".repeat(65) + "</svg>").getBytes(utf8)));
        assertFalse(TokenIcons.cheapSvg(("<svg>" + "<use\nhref=\"#a\"/>".repeat(65) + "</svg>").getBytes(utf8)));
        assertFalse(TokenIcons.cheapSvg("<svg><image href=\"data&#58;image/png;base64,AA\"/></svg>".getBytes(utf8)));
        assertFalse(TokenIcons.cheapSvg("<svg><image href=\"data&#x3A;image/png;base64,AA\"/></svg>".getBytes(utf8)));
        assertFalse(TokenIcons.cheapSvg("<svg><image href=\"DATA:image/png;base64,AA\"/></svg>".getBytes(utf8)));
        assertFalse(TokenIcons.cheapSvg("<!DOCTYPE svg [<!entity a \"aa\">]><svg/>".getBytes(utf8)));
        // What only looks like them, and references that stand for nothing, are no reason.
        assertTrue(TokenIcons.cheapSvg(("<svg>" + "<user/>".repeat(65) + "<text>&#169; &#xFFFFFFF; metadata</text></svg>").getBytes(utf8)));
    }

    @Test
    public void keepsNoFullArtOfAHiddenToken() throws java.io.IOException {
        java.io.File dir = java.nio.file.Files.createTempDirectory("full").toFile();
        java.io.File file = new java.io.File(dir, "art");
        String category = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";
        TokenLabels.hold(java.util.Collections.singleton(category));
        try {
            assertFalse(TokenIcons.saveFull(category, file, new byte[]{1, 2, 3}, 100));
            assertFalse(file.exists());
        } finally {
            TokenLabels.hold(java.util.Collections.emptySet());
            file.delete();
            dir.delete();
        }
    }

    @Test
    public void keepsWhatWasFoundSavedUntilAForget() {
        TokenIcons.SavedChecks found = new TokenIcons.SavedChecks();
        assertFalse(found.has("aa-01 ipfs://one", 0));
        found.add("aa-01 ipfs://one", 0);
        assertTrue(found.has("aa-01 ipfs://one", 0));
        // Another URI is no art saved from this one.
        assertFalse(found.has("aa-01 ipfs://two", 0));

        // A forget, which can have removed it, has it checked again.
        assertFalse(found.has("aa-01 ipfs://one", 1));
        // What was found before a forget that came since isn't kept.
        found.add("aa-01 ipfs://one", 0);
        assertFalse(found.has("aa-01 ipfs://one", 1));
        // What was found after it is, also when it's told before the forget was seen.
        found.add("aa-01 ipfs://one", 2);
        assertTrue(found.has("aa-01 ipfs://one", 2));
    }
}
