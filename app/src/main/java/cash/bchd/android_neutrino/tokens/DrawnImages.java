package cash.bchd.android_neutrino.tokens;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * DrawnImages keeps what was drawn, by key, up to a size in all, letting what was used longest ago
 * go first.
 */
final class DrawnImages<T> {

    private final long limit;
    private final ToLongFunction<T> sizeOf;
    // In the order of their use, the one used longest ago first.
    private final LinkedHashMap<String, T> images = new LinkedHashMap<>(16, 0.75f, true);
    private long size;

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
     * removeStartingWith lets go of what's under the keys that start with the prefix.
     */
    synchronized void removeStartingWith(String prefix) {
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
    }
}
