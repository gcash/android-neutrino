package cash.bchd.android_neutrino.wallet;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LatestRepliesTest {

    @Test
    public void dropsAReplyThatWasOvertaken() {
        LatestReplies replies = new LatestReplies();
        long first = replies.next();
        long second = replies.next();
        long third = replies.next();
        List<Long> delivered = new ArrayList<>();
        replies.deliver(second, () -> delivered.add(second));
        replies.deliver(first, () -> delivered.add(first));
        replies.deliver(third, () -> delivered.add(third));
        assertEquals(Arrays.asList(second, third), delivered);
    }

    @Test
    public void deliversEachReplyOnce() {
        LatestReplies replies = new LatestReplies();
        long reply = replies.next();
        List<Long> delivered = new ArrayList<>();
        replies.deliver(reply, () -> delivered.add(reply));
        replies.deliver(reply, () -> delivered.add(reply));
        assertEquals(1, delivered.size());
    }
}
