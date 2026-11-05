package cash.bchd.android_neutrino.wallet;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class BcmrFilesTest {

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
}
