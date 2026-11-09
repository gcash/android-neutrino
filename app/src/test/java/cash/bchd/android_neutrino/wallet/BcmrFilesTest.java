package cash.bchd.android_neutrino.wallet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

public class BcmrFilesTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void heightComesFromTheHeader() {
        byte[] table = new byte[20];
        byte[] magic = "BCMRHNT1".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(magic, 0, table, 0, magic.length);
        table[8] = 0x0d;
        table[9] = 0x63;
        table[10] = 0x0e;
        assertEquals(0x0e630dL, BcmrFiles.height(table));
    }

    @Test
    public void anythingElseHasNoHeight() {
        assertEquals(-1, BcmrFiles.height(new byte[20]));
        assertEquals(-1, BcmrFiles.height(new byte[3]));
    }

    // table returns a table of the height, with entries and publications of zeros.
    private static byte[] table(int height, int entries, int publications) {
        ByteBuffer table = ByteBuffer.allocate(20 + entries * 72 + publications * 36).order(ByteOrder.LITTLE_ENDIAN);
        table.put("BCMRHNT1".getBytes(StandardCharsets.US_ASCII));
        table.putInt(height).putInt(entries).putInt(publications);
        return table.array();
    }

    @Test
    public void sizeComesFromTheCounts() {
        assertEquals(20 + 3 * 72 + 2 * 36, BcmrFiles.size(table(10, 3, 2)));
        assertEquals(-1, BcmrFiles.size(new byte[20]));
    }

    private File installed(byte[] table) throws IOException {
        File file = folder.newFile();
        Files.write(file.toPath(), table);
        return file;
    }

    @Test
    public void keepsOnlyAWholeTableThatsNewerOrTheSame() throws IOException {
        byte[] bundled = table(100, 3, 1);
        assertTrue(BcmrFiles.keeps(installed(table(101, 5, 0)), bundled));
        assertTrue(BcmrFiles.keeps(installed(table(100, 3, 1)), bundled));
        assertFalse(BcmrFiles.keeps(installed(table(99, 5, 0)), bundled));
        assertFalse(BcmrFiles.keeps(installed(table(100, 4, 1)), bundled));
        byte[] newer = table(101, 5, 0);
        assertFalse(BcmrFiles.keeps(installed(Arrays.copyOf(newer, newer.length - 1)), bundled));
        assertFalse(BcmrFiles.keeps(installed(Arrays.copyOf(newer, 10)), bundled));
        assertFalse(BcmrFiles.keeps(installed(new byte[newer.length]), bundled));
    }
}
