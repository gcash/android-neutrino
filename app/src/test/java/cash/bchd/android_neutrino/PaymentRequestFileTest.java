package cash.bchd.android_neutrino;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class PaymentRequestFileTest {

    // EndlessStream counts the bytes read from it, of which it has no end.
    private static final class EndlessStream extends InputStream {
        long read;

        @Override
        public int read() {
            read++;
            return 0;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            read += len;
            return len;
        }
    }

    @Test
    public void readsARequestUpToTheLimit() throws IOException {
        byte[] request = new byte[PaymentRequestFile.MAX_SIZE];
        request[0] = 1;
        request[request.length - 1] = 2;
        assertArrayEquals(request, PaymentRequestFile.read(new ByteArrayInputStream(request)));
        assertArrayEquals(new byte[]{1, 2, 3}, PaymentRequestFile.read(new ByteArrayInputStream(new byte[]{1, 2, 3})));
    }

    @Test
    public void refusesALargerRequestWithoutReadingAllOfIt() {
        byte[] request = new byte[PaymentRequestFile.MAX_SIZE + 1];
        assertThrows(PaymentRequestFile.TooLargeException.class,
                () -> PaymentRequestFile.read(new ByteArrayInputStream(request)));

        EndlessStream endless = new EndlessStream();
        assertThrows(PaymentRequestFile.TooLargeException.class, () -> PaymentRequestFile.read(endless));
        assertEquals(PaymentRequestFile.MAX_SIZE + 1, endless.read);
    }
}
