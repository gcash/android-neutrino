package cash.bchd.android_neutrino;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TokenActivityTest {

    @Test
    public void aFetchIsOverWhenItsReadIsOrItsTimeIs() {
        assertFalse(TokenActivity.fetchOver(0, 5, true));
        assertFalse(TokenActivity.fetchOver(10, 5, false));
        assertTrue(TokenActivity.fetchOver(10, 5, true));
        assertTrue(TokenActivity.fetchOver(10, 10, false));
    }
}
