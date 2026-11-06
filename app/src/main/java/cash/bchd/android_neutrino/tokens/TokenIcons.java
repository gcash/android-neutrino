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

    // nftFile returns the file of an NFT's own art, under a name no token ID has.
    private static File nftFile(Context context, String category, String commitment) {
        return file(context, category + "-" + commitment);
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
        if (showing(category) && file.exists()) {
            Bitmap icon = decode(file.getPath(), size);
            if (icon != null) {
                return icon;
            }
        }
        return Identicon.of(category).bitmap(size);
    }

    /**
     * nftBitmap returns an NFT's own art at the size, or else its token's icon.
     */
    public static Bitmap nftBitmap(Context context, String category, String commitment, int size) {
        File file = nftFile(context, category, commitment);
        if (showing(category) && file.exists()) {
            Bitmap art = decode(file.getPath(), size);
            if (art != null) {
                return art;
            }
        }
        return bitmap(context, category, size);
    }

    /**
     * showing returns whether a token's images are to show, and so to load: the setting is on, or
     * the user loaded them from the token's menu.
     */
    public static boolean showing(String category) {
        return showAll || chosen.contains(category);
    }

    /**
     * has returns whether a token's icon was loaded.
     */
    public static boolean has(Context context, String category) {
        return file(context, category).exists();
    }

    /**
     * hasNft returns whether an NFT's own art was loaded.
     */
    public static boolean hasNft(Context context, String category, String commitment) {
        return nftFile(context, category, commitment).exists();
    }

    /**
     * save keeps an icon if it's an image this device can show, and returns whether it did.
     */
    public static boolean save(Context context, String category, byte[] data) {
        return save(file(context, category), data);
    }

    /**
     * saveNft keeps an NFT's own art as save keeps an icon.
     */
    public static boolean saveNft(Context context, String category, String commitment, byte[] data) {
        return save(nftFile(context, category, commitment), data);
    }

    private static boolean save(File file, byte[] data) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        if (!usable(bounds.outWidth, bounds.outHeight)) {
            return false;
        }
        File dir = file.getParentFile();
        if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) {
            return false;
        }
        File tmp = new File(dir, file.getName() + ".tmp");
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
