package cash.bchd.android_neutrino.tokens;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.media.MediaDataSource;
import android.media.MediaMetadataRetriever;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import cash.bchd.android_neutrino.R;

/**
 * TokenIcons keeps the icons the user chose to load, as files in the app's data. An icon is a file
 * from a stranger, so it's decoded with its size checked first, and scaled down as it's read. A
 * token without one keeps its identicon.
 */
public final class TokenIcons {

    // The largest side of an image it will decode.
    private static final int MAX_SIDE = 4096;
    // What the videos and animations kept to play can take up.
    private static final long MAX_MOTION_SIZE = 32 << 20;

    private static volatile boolean showAll;
    private static volatile Set<String> chosen = Collections.emptySet();

    private TokenIcons() {
    }

    private static File file(Context context, String category) {
        return new File(new File(context.getFilesDir(), "token-icons"), category);
    }

    // motionMarker returns the file that says an NFT's art moves.
    private static File motionMarker(Context context, String category, String commitment) {
        return new File(nftFile(context, category, commitment).getPath() + ".motion");
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
        return save(file(context, category), data, largestShown(context));
    }

    /**
     * saveNft keeps an NFT's own art as save keeps an icon.
     */
    public static boolean saveNft(Context context, String category, String commitment, byte[] data) {
        boolean moves = isVideo(data) || isAnimatedGif(data);
        boolean saved = save(nftFile(context, category, commitment), data, largestShown(context));
        if (saved && moves) {
            try {
                motionMarker(context, category, commitment).createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return saved;
    }

    /**
     * moves returns whether an NFT's art is a video or an animation, by what was saved of it or by
     * its URI, for art saved before the app kept that.
     */
    public static boolean moves(Context context, String category, String commitment, String uri) {
        return motionMarker(context, category, commitment).exists() || movingUri(uri);
    }

    static boolean movingUri(String uri) {
        String lower = uri.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mp4") || lower.endsWith(".gif");
    }

    /**
     * isGif returns whether a file starts as a GIF does.
     */
    public static boolean isGif(byte[] start) {
        return start.length >= 4 && start[0] == 'G' && start[1] == 'I' && start[2] == 'F' && start[3] == '8';
    }

    private static boolean isVideo(byte[] data) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        return bounds.outWidth <= 0;
    }

    private static boolean isAnimatedGif(byte[] data) {
        if (!isGif(data)) {
            return false;
        }
        try {
            return ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(data)))
                    instanceof AnimatedImageDrawable;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    // videoFrame returns the first frame of a video, or null if it isn't one this device can read.
    private static Bitmap videoFrame(byte[] data) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(new BytesSource(data));
            return retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
        } catch (RuntimeException e) {
            return null;
        } finally {
            try {
                retriever.release();
            } catch (IOException | RuntimeException e) {
                e.printStackTrace();
            }
        }
    }

    private static final class BytesSource extends MediaDataSource {
        private final byte[] data;

        BytesSource(byte[] data) {
            this.data = data;
        }

        @Override
        public int readAt(long position, byte[] buffer, int offset, int size) {
            if (position >= data.length) {
                return -1;
            }
            int read = (int) Math.min(size, data.length - position);
            System.arraycopy(data, (int) position, buffer, offset, read);
            return read;
        }

        @Override
        public long getSize() {
            return data.length;
        }

        @Override
        public void close() {
        }
    }

    /**
     * motionFile returns where a moving NFT's file is kept once it was asked to play.
     */
    public static File motionFile(Context context, String category, String commitment) {
        return new File(new File(context.getCacheDir(), "nft-motion"), category + "-" + commitment);
    }

    /**
     * saveMotion keeps a moving NFT's file to play, and the newest such files up to a limit, as
     * only the user's taps bring them here.
     */
    public static boolean saveMotion(Context context, String category, String commitment, byte[] data) {
        File file = motionFile(context, category, commitment);
        File dir = file.getParentFile();
        if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) {
            return false;
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(data);
        } catch (IOException e) {
            file.delete();
            return false;
        }
        trimMotion(dir, file);
        return true;
    }

    private static void trimMotion(File dir, File keep) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        long total = 0;
        for (File file : files) {
            total += file.length();
            if (total > MAX_MOTION_SIZE && !file.equals(keep)) {
                file.delete();
            }
        }
    }

    // largestShown returns the largest side any screen shows an image at on this device, which is
    // all that's worth keeping, as a collection's art can be megabytes each.
    private static int largestShown(Context context) {
        return context.getResources().getDimensionPixelSize(R.dimen.nft_screen_icon);
    }

    private static boolean save(File file, byte[] data, int maxSide) {
        // A video keeps its first frame, as lists show images.
        Bitmap frame = null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        if (bounds.outWidth <= 0) {
            frame = videoFrame(data);
            if (frame == null) {
                return false;
            }
        } else if (!usable(bounds.outWidth, bounds.outHeight)) {
            return false;
        }
        File dir = file.getParentFile();
        if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) {
            return false;
        }
        // Its own file, as two screens can load the same image at once.
        File tmp;
        try {
            tmp = File.createTempFile("icon", ".tmp", dir);
        } catch (IOException e) {
            return false;
        }
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            if (frame != null) {
                if (!write(frame, maxSide, out)) {
                    tmp.delete();
                    return false;
                }
            } else if (Math.max(bounds.outWidth, bounds.outHeight) <= maxSide) {
                out.write(data);
            } else if (!shrink(data, bounds, maxSide, out)) {
                tmp.delete();
                return false;
            }
        } catch (IOException e) {
            tmp.delete();
            return false;
        }
        return tmp.renameTo(file);
    }

    // shrink writes the image with its largest side at maxSide, in its proportions.
    private static boolean shrink(byte[] data, BitmapFactory.Options bounds, int maxSide, FileOutputStream out) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide);
        Bitmap decoded = BitmapFactory.decodeByteArray(data, 0, data.length, options);
        return decoded != null && write(decoded, maxSide, out);
    }

    // write writes a bitmap with its largest side at most maxSide, in its proportions.
    private static boolean write(Bitmap bitmap, int maxSide, FileOutputStream out) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float scale = Math.min(1f, (float) maxSide / Math.max(width, height));
        Bitmap small = Bitmap.createScaledBitmap(bitmap, Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale)), true);
        return small.compress(Bitmap.CompressFormat.PNG, 100, out);
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
