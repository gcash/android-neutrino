package cash.bchd.android_neutrino.wallet;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TransactionDataTest {

    // A sent and a received transaction as TransactionStore saved them before TransactionData had
    // moreRecipients and toYourself, written on Android by that version of the class.
    private static final String SAVED_BEFORE_RECIPIENTS =
            "rO0ABXNyABNqYXZhLnV0aWwuQXJyYXlMaXN0eIHSHZnHYZ0DAAFJAARzaXpleHAAAAACdwQAAAACc3IAMWNhc2guYmNoZC5h" +
            "bmRyb2lkX25ldXRyaW5vLndhbGxldC5UcmFuc2FjdGlvbkRhdGF//edCsc/8UQIACUoABmFtb3VudEkABmhlaWdodFoACGlu" +
            "Y29taW5nSgAJdGltZXN0YW1wTAAKZmlhdEFtb3VudHQAEkxqYXZhL2xhbmcvU3RyaW5nO0wADGZpYXRDdXJyZW5jeXEAfgAD" +
            "TAAEbWVtb3EAfgADTAAJdG9BZGRyZXNzcQB+AANMAAR0eGlkcQB+AAN4cP///////+x4AA4JwAAAAAAAaP5bYHQABSQxLjM5" +
            "dAADVVNEdAAFbHVuY2h0ADZiaXRjb2luY2FzaDpxcmhlYTAzMDc0MDczZmYzenY5d2hoMG5nZ3hjN2swM3NzaDhqdjlta3h0" +
            "AEBmNDE4NGZjNTk2NDAzYjlkNjM4NzgzY2Y1N2FkZmU0Yzc1YzYwNWY2MzU2ZmJjOTEzMzg1MzBlOTgzMWU5ZTE2c3EAfgAC" +
            "AAAAAAABhqAADgm2AQAAAABo/NTAdAAGJDI3LjgwcQB+AAZ0AABxAH4ADHQAQDBlM2UyMzU3ZTgwNmI2Y2RiMWY3MGI1NGMz" +
            "YTNhMTdiNjcxNGVlMWYwZTY4YmViYjQ0YTc0YjFlZmQ1MTIwOTh4";

    @Test
    public void readsTheTransactionsSavedBeforeItHadRecipients() throws Exception {
        byte[] saved = Base64.getDecoder().decode(SAVED_BEFORE_RECIPIENTS);
        List<TransactionData> list;
        try (ObjectInputStream is = new ObjectInputStream(new ByteArrayInputStream(saved))) {
            list = (ArrayList<TransactionData>) is.readObject();
        }
        assertEquals(2, list.size());

        TransactionData sent = list.get(0);
        assertEquals("f4184fc596403b9d638783cf57adfe4c75c605f6356fbc91338530e9831e9e16", sent.getTxid());
        assertFalse(sent.getIncoming());
        assertEquals("lunch", sent.getMemo());
        assertEquals(-5000, sent.getAmount());
        assertEquals("$1.39", sent.getFiatAmount());
        assertEquals("USD", sent.getFiatCurrency());
        assertEquals(1761500000L, sent.getTimestamp());
        assertEquals("bitcoincash:qrhea03074073ff3zv9whh0nggxc7k03ssh8jv9mkx", sent.getToAddress());
        assertEquals(920000, sent.getHeight());
        assertEquals(0, sent.getMoreRecipients());
        assertFalse(sent.getToYourself());
        assertEquals(TransactionData.SentTo.ADDRESS, sent.getSentTo());

        TransactionData received = list.get(1);
        assertEquals("0e3e2357e806b6cdb1f70b54c3a3a17b6714ee1f0e68bebb44a74b1efd512098", received.getTxid());
        assertTrue(received.getIncoming());
        assertEquals(100000, received.getAmount());
        assertEquals("$27.80", received.getFiatAmount());
        assertEquals(1761400000L, received.getTimestamp());
        assertEquals(919990, received.getHeight());
    }
}
