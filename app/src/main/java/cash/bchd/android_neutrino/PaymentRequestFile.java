package cash.bchd.android_neutrino;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * PaymentRequestFile reads a BIP70 payment request that a merchant hands over as a file, with
 * the application/bitcoincash-paymentrequest type, as a browser opens one that it downloaded.
 */
final class PaymentRequestFile {

    static final String MIME_TYPE = "application/bitcoincash-paymentrequest";

    // The largest payment request that BIP70 allows, in bytes.
    static final int MAX_SIZE = 50000;

    // The extra of an intent that carries a payment request to the screen that pays it.
    static final String EXTRA = "paymentRequest";

    /**
     * TooLargeException is thrown for a payment request larger than BIP70 allows.
     */
    static final class TooLargeException extends IOException {
        TooLargeException() {
            super("payment request larger than " + MAX_SIZE + " bytes");
        }
    }

    private PaymentRequestFile() {
    }

    /**
     * read returns the bytes of a payment request, reading at most one byte more than BIP70
     * allows, to tell one that is larger.
     */
    static byte[] read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf, 0, Math.min(buf.length, MAX_SIZE + 1 - out.size()))) > 0) {
            out.write(buf, 0, n);
            if (out.size() > MAX_SIZE) {
                throw new TooLargeException();
            }
        }
        return out.toByteArray();
    }
}
