package cash.bchd.android_neutrino.wallet;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
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

    // A received transaction that moved tokens as TransactionStore saved it before TokenChange
    // had the commitments of the NFTs, written by that version of the classes.
    private static final String SAVED_BEFORE_COMMITMENTS =
            "rO0ABXNyABNqYXZhLnV0aWwuQXJyYXlMaXN0eIHSHZnHYZ0DAAFJAARzaXpleHAAAAABdwQAAAABc3IAMWNhc2guYmNoZC5h" +
            "bmRyb2lkX25ldXRyaW5vLndhbGxldC5UcmFuc2FjdGlvbkRhdGF//edCsc/8UQIADEoABmFtb3VudEkABmhlaWdodFoACGlu" +
            "Y29taW5nSQAObW9yZVJlY2lwaWVudHNKAAl0aW1lc3RhbXBaAAp0b1lvdXJzZWxmTAAKZmlhdEFtb3VudHQAEkxqYXZhL2xh" +
            "bmcvU3RyaW5nO0wADGZpYXRDdXJyZW5jeXEAfgADTAAEbWVtb3EAfgADTAAJdG9BZGRyZXNzcQB+AANMAAZ0b2tlbnN0ABVM" +
            "amF2YS91dGlsL0FycmF5TGlzdDtMAAR0eGlkcQB+AAN4cAAAAAAAAAPoAA4KJAEAAAAAAAAAAGkFOTAAdAAAcQB+AAZxAH4A" +
            "BnEAfgAGc3EAfgAAAAAAAncEAAAAAnNyAC1jYXNoLmJjaGQuYW5kcm9pZF9uZXV0cmluby53YWxsZXQuVG9rZW5DaGFuZ2UA" +
            "AAAAAAAAAQIAA0oABmFtb3VudEkABG5mdHNMAAhjYXRlZ29yeXEAfgADeHAAAAAAAAAAGQAAAAB0AAJiYnNxAH4ACAAAAAAA" +
            "AAAAAAAAAXQAAmNjeHQAQDBlNjg2N2EzMDViOWM5ZDRkMzY5ZmM4NTBiMDEyYzRlOGY1MzFmMjE0ZTM2OTM2YjhjNzgyMmI3" +
            "MDM5YzNhYTB4";

    @Test
    public void readsTheTokensSavedBeforeTheyHadCommitments() throws Exception {
        byte[] saved = Base64.getDecoder().decode(SAVED_BEFORE_COMMITMENTS);
        List<?> list;
        try (ObjectInputStream is = new ObjectInputStream(new ByteArrayInputStream(saved))) {
            list = (List<?>) is.readObject();
        }
        TransactionData tx = (TransactionData) list.get(0);
        assertEquals("0e6867a305b9c9d4d369fc850b012c4e8f531f214e36936b8c7822b7039c3aa0", tx.getTxid());
        assertEquals(Arrays.asList(new TokenChange("bb", 25, 0), new TokenChange("cc", 0, 1)), tx.getTokens());
        assertEquals(Collections.emptyList(), tx.getTokens().get(1).nftsReceived());
        assertEquals(0, tx.getSpendableAmount());
    }

    @Test
    public void readsTheTransactionsSavedBeforeItHadRecipients() throws Exception {
        byte[] saved = Base64.getDecoder().decode(SAVED_BEFORE_RECIPIENTS);
        List<?> list;
        try (ObjectInputStream is = new ObjectInputStream(new ByteArrayInputStream(saved))) {
            list = (List<?>) is.readObject();
        }
        assertEquals(2, list.size());

        TransactionData sent = (TransactionData) list.get(0);
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
        // Saved before the app read tokens.
        assertEquals(Collections.emptyList(), sent.getTokens());

        TransactionData received = (TransactionData) list.get(1);
        assertEquals("0e3e2357e806b6cdb1f70b54c3a3a17b6714ee1f0e68bebb44a74b1efd512098", received.getTxid());
        assertTrue(received.getIncoming());
        assertEquals(100000, received.getAmount());
        assertEquals("$27.80", received.getFiatAmount());
        assertEquals(1761400000L, received.getTimestamp());
        assertEquals(919990, received.getHeight());
    }

    @Test
    public void theSignOfAFiatAmountComesFirst() {
        TransactionData tx = new TransactionData("", false, "", 0, "$-134.10", "USD", 0, "", 0);
        assertEquals("-$134.10", tx.getFiatAmount());
        tx.setFiatAmount("$134.10");
        assertEquals("$134.10", tx.getFiatAmount());
        tx.setFiatAmount("-134.10");
        assertEquals("-134.10", tx.getFiatAmount());
        tx.setFiatAmount("");
        assertEquals("", tx.getFiatAmount());
    }

    @Test
    public void detailsShowWhatsSpendableOfATokenTransaction() {
        TransactionData plain = new TransactionData("", false, "", -5000, "", "USD", 0, "", 0);
        plain.setSpendableAmount(-5000);
        assertEquals(-5000, plain.shownAmount());

        TransactionData nft = new TransactionData("", false, "", -5001000, "", "USD", 0, "", 0);
        nft.setTokens(Collections.singletonList(new TokenChange("c", 0, 1)));
        nft.setSpendableAmount(-5002000);
        assertEquals(-5002000, nft.shownAmount());
    }

    @Test
    public void aTokenTransactionShowsTheDirectionOfItsRow() {
        TransactionData plain = new TransactionData("", true, "", 5000, "", "USD", 0, "", 0);
        assertTrue(plain.shownIncoming());
        assertFalse(plain.swapped());

        TransactionData received = new TransactionData("", false, "", -500, "", "USD", 0, "", 0);
        received.setTokens(Collections.singletonList(new TokenChange("c", 0, 1)));
        assertTrue(received.shownIncoming());

        TransactionData swap = new TransactionData("", false, "", -500, "", "USD", 0, "", 0);
        swap.setTokens(Collections.singletonList(new TokenChange("c", 0, 0,
                Collections.singletonList("02"), Collections.singletonList("01"))));
        assertTrue(swap.swapped());
    }
}
