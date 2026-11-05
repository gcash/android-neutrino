package cash.bchd.android_neutrino.tokens;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * TokenIcons keeps the icons the user chose to load, as files in the app's data. An icon is a file
 * from a stranger, so it's decoded with its size checked first, and scaled down as it's read. A
 * token without one keeps its identicon.
 */
public final class TokenIcons {

    // The largest side of an image it will decode.
    private static final int MAX_SIDE = 4096;

    private static volatile boolean showAll;
    private static volatile Set<String> chosen = Collections.emptySet();

    private TokenIcons() {
    }

    private static File file(Context context, String category) {
        return new File(new File(context.getFilesDir(), "token-icons"), category);
    }

    /**
     * configure says which icons to show: all that were loaded, with the setting on, or else only
     * those the user loaded from a token's menu.
     */
    public static void configure(boolean all, Set<String> chosen) {
        showAll = all;
        TokenIcons.chosen = new HashSet<>(chosen);
    }

    /**
     * bitmap returns a token's icon at the size, or its identicon if it has none to show.
     */
    public static Bitmap bitmap(Context context, String category, int size) {
        File file = file(context, category);
        if ((showAll || chosen.contains(category)) && file.exists()) {
            Bitmap icon = decode(file.getPath(), size);
            if (icon != null) {
                return icon;
            }
        }
        return Identicon.of(category).bitmap(size);
    }

    /**
     * has returns whether a token's icon was loaded.
     */
    public static boolean has(Context context, String category) {
        return file(context, category).exists();
    }

    /**
     * save keeps an icon if it's an image this device can show, and returns whether it did.
     */
    public static boolean save(Context context, String category, byte[] data) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        if (!usable(bounds.outWidth, bounds.outHeight)) {
            return false;
        }
        File file = file(context, category);
        File dir = file.getParentFile();
        if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) {
            return false;
        }
        File tmp = new File(dir, category + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(data);
        } catch (IOException e) {
            return false;
        }
        return tmp.renameTo(file);
    }

    /**
     * usable returns whether an image's size is one to decode.
     */
    static boolean usable(int width, int height) {
        return width > 0 && height > 0 && width <= MAX_SIDE && height <= MAX_SIDE;
    }

    /**
     * sampleSize returns how much to scale an image down while it's read, as a power of two, so
     * that it's still at least the size.
     */
    static int sampleSize(int width, int height, int size) {
        int sample = 1;
        while (Math.min(width, height) / (sample * 2) >= size) {
            sample *= 2;
        }
        return sample;
    }

    private static Bitmap decode(String path, int size) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        if (!usable(options.outWidth, options.outHeight)) {
            return null;
        }
        options.inJustDecodeBounds = false;
        options.inSampleSize = sampleSize(options.outWidth, options.outHeight, size);
        Bitmap decoded = BitmapFactory.decodeFile(path, options);
        return decoded == null ? null : Bitmap.createScaledBitmap(decoded, size, size, true);
    }
}
