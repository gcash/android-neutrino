package cash.bchd.android_neutrino;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NetworkChangesTest {

    @Test
    public void tellsOnlyOfAnotherNetwork() {
        NetworkChanges changes = new NetworkChanges();
        assertFalse(changes.changed("100"));
        assertFalse(changes.changed("100"));
        assertTrue(changes.changed("101"));
        assertFalse(changes.changed("101"));
        assertTrue(changes.changed("100"));
    }
}
