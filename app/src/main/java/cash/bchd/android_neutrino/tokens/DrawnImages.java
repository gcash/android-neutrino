package cash.bchd.android_neutrino.tokens;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * DrawnImages keeps what was drawn, by key, up to a size in all, letting what was used longest ago
 * go first. Each scope, a prefix of keys that goes at once, has a generation, so that a drawing
 * begun before its scope went can be told and let go.
 */
final class DrawnImages<T> {

    private final long limit;
    private final ToLongFunction<T> sizeOf;
    // In the order of their use, the one used longest ago first.
    private final LinkedHashMap<String, T> images = new LinkedHashMap<>(16, 0.75f, true);
    private long size;
    // The generations of the scopes let go of, and of everything, which clear lets go of.
    private final Map<String, Long> generations = new HashMap<>();
    private long everything;

    DrawnImages(long limit, ToLongFunction<T> sizeOf) {
        this.limit = limit;
        this.sizeOf = sizeOf;
    }

    synchronized T get(String key) {
        return images.get(key);
    }

    synchronized void put(String key, T image) {
        T before = images.put(key, image);
        if (before != null) {
            size -= sizeOf.applyAsLong(before);
        }
        size += sizeOf.applyAsLong(image);
        Iterator<Map.Entry<String, T>> oldest = images.entrySet().iterator();
        while (size > limit && oldest.hasNext()) {
            size -= sizeOf.applyAsLong(oldest.next().getValue());
            oldest.remove();
        }
    }

    /**
     * generations returns the generations of the scopes now, for putIfCurrent.
     */
    synchronized long[] generations(String... scopes) {
        long[] now = new long[scopes.length + 1];
        now[0] = everything;
        for (int i = 0; i < scopes.length; i++) {
            now[i + 1] = generations.getOrDefault(scopes[i], 0L);
        }
        return now;
    }

    /**
     * putIfCurrent keeps an image, and returns whether it did, only if none of its scopes went
     * since their generations were seen.
     */
    synchronized boolean putIfCurrent(String key, T image, String[] scopes, long[] seen) {
        long[] now = generations(scopes);
        for (int i = 0; i < now.length; i++) {
            if (now[i] != seen[i]) {
                return false;
            }
        }
        put(key, image);
        return true;
    }

    /**
     * removeStartingWith lets go of what's under the keys that start with the prefix, a scope.
     */
    synchronized void removeStartingWith(String prefix) {
        generations.merge(prefix, 1L, Long::sum);
        Iterator<Map.Entry<String, T>> entries = images.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, T> entry = entries.next();
            if (entry.getKey().startsWith(prefix)) {
                size -= sizeOf.applyAsLong(entry.getValue());
                entries.remove();
            }
        }
    }

    synchronized void clear() {
        images.clear();
        size = 0;
        everything++;
    }
}
