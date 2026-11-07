package cash.bchd.android_neutrino.tokens;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.media.MediaDataSource;
import android.media.MediaMetadataRetriever;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import cash.bchd.android_neutrino.R;

/**
 * TokenIcons keeps the icons the user chose to load, as files in the app's data. An icon is a file
 * from a stranger, so it's decoded with its size checked first, and scaled down as it's read. A
 * token without one keeps its identicon.
 */
public final class TokenIcons {

    // The largest side of an image it will decode.
    private static final int MAX_SIDE = 4096;
    // The most pixels of a still image to bring down to full size, so that a large one isn't left
    // a thumbnail. It's by pixels, as some formats are read whole, whatever size they're read to.
    private static final long MAX_FULL_PIXELS = 1L << 25;
    // The side an NFT's art is kept at, which shows it in a list and large in its popup, as many
    // NFTs have no other image to fetch. It's a little under a phone's width, as all of that takes
    // twice the space for what can hardly be seen.
    private static final int NFT_SIDE = 768;
    // What the videos and animations kept to play can take up.
    private static final long MAX_MOTION_SIZE = 32 << 20;
    // The largest file it keeps as it came, as a small image can carry any amount besides.
    private static final int MAX_KEPT_SIZE = 256 << 10;
    // The longest commitment a file's name holds whole, and how large an animation is decoded to
    // tell that it moves.
    private static final int MAX_NAME_COMMITMENT = 64;
    private static final int ANIMATION_CHECK_SIDE = 16;
    private static final String ICON_DIR = "token-icons";
    private static final String MOTION_DIR = "nft-motion";

    // Where a WebP's extended header keeps its flags, and the one for an animation.
    private static final int WEBP_FLAGS = 20;
    private static final int WEBP_ANIMATED = 0x02;

    // Counts what was saved, for a screen to tell that it drew older images.
    private static final AtomicLong VERSION = new AtomicLong();

    private static volatile boolean showAll;
    private static volatile Set<String> chosen = Collections.emptySet();

    private TokenIcons() {
    }

    private static File file(Context context, String category) {
        return new File(new File(context.getFilesDir(), ICON_DIR), category);
    }

    // nftName returns the name of an NFT's files: its token ID and commitment, with a long
    // commitment hashed, as a file's name can't be as long as a commitment can.
    private static String nftName(String category, String commitment) {
        if (commitment.length() <= MAX_NAME_COMMITMENT) {
            return category + "-" + commitment;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(commitment.getBytes(StandardCharsets.UTF_8));
            return category + "-" + new BigInteger(1, digest).toString(16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // sourceFile returns the file that says what URI an NFT's art was saved from.
    private static File sourceFile(Context context, String category, String commitment) {
        return new File(nftFile(context, category, commitment).getPath() + ".source");
    }

    /**
     * savedFrom returns whether an NFT's saved art came from the URI, at the size it's kept at now.
     * Art saved by earlier versions says nothing, as it's smaller, and a registry can come to name
     * other art.
     */
    public static boolean savedFrom(Context context, String category, String commitment, String uri) {
        try {
            byte[] saved = Files.readAllBytes(sourceFile(context, category, commitment).toPath());
            return uri.equals(new String(saved, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return false;
        }
    }

    // motionMarker returns the file that says an NFT's art moves.
    private static File motionMarker(Context context, String category, String commitment) {
        return new File(nftFile(context, category, commitment).getPath() + ".motion");
    }

    // nftFile returns the file of an NFT's own art, under a name no token ID has.
    private static File nftFile(Context context, String category, String commitment) {
        return file(context, nftName(category, commitment));
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
     * listBitmap returns a token's icon where no badge shows, as in a transaction's row: the
     * identicon of a look-alike, as its icon is made to read as another token's.
     */
    public static Bitmap listBitmap(Context context, String category, int size) {
        return TokenLabels.isLookAlike(category) ? Identicon.of(category).bitmap(size) : bitmap(context, category, size);
    }

    /**
     * listNftBitmap returns an NFT's art where no badge shows, as listBitmap does a token's.
     */
    public static Bitmap listNftBitmap(Context context, String category, String commitment, int size) {
        return TokenLabels.isLookAlike(category) ? Identicon.of(category).bitmap(size)
                : nftBitmap(context, category, commitment, size);
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
    public static boolean saveNft(Context context, String category, String commitment, byte[] data, String uri) {
        boolean moves = moves(data);
        boolean saved = save(nftFile(context, category, commitment), data, NFT_SIDE);
        if (saved) {
            try {
                Files.write(sourceFile(context, category, commitment).toPath(), uri.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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
     * canAnimate returns whether a file starts as a GIF does, which may have one frame, or as a WebP
     * that says it's animated.
     */
    static boolean canAnimate(byte[] start) {
        if (start.length >= 4 && start[0] == 'G' && start[1] == 'I' && start[2] == 'F' && start[3] == '8') {
            return true;
        }
        return start.length > WEBP_FLAGS && startsWith(start, 0, "RIFF") && startsWith(start, 8, "WEBPVP8X")
                && (start[WEBP_FLAGS] & WEBP_ANIMATED) != 0;
    }

    private static boolean startsWith(byte[] data, int at, String text) {
        for (int i = 0; i < text.length(); i++) {
            if (data[at + i] != text.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    // moves returns whether a file is a video, which no image decoder reads, or an animation. The
    // file is a stranger's, so an image's size is checked first, and an animation is only looked at
    // as a thumbnail.
    private static boolean moves(byte[] data) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        if (bounds.outWidth <= 0) {
            return true;
        }
        if (!canAnimate(data) || !usable(bounds.outWidth, bounds.outHeight)) {
            return false;
        }
        try {
            return ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(data)),
                    (decoder, info, source) -> decoder.setTargetSize(ANIMATION_CHECK_SIDE, ANIMATION_CHECK_SIDE))
                    instanceof AnimatedImageDrawable;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    // videoFrame returns the first frame of a video, or null if it isn't one this device can read.
    private static Bitmap videoFrame(byte[] data, int maxSide) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(new BytesSource(data));
            return retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxSide, maxSide);
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
     * fullFile returns where an NFT's art in full is kept once it was opened, by its URI too, as a
     * registry can come to name other art.
     */
    public static File fullFile(Context context, String category, String commitment, String uri) {
        String name = nftName(category, commitment) + "-" + Integer.toHexString(uri.hashCode());
        return new File(new File(context.getCacheDir(), MOTION_DIR), name);
    }

    /**
     * saveFull keeps an NFT's art in full to show large, and the newest such files up to a limit,
     * as only the user's taps bring them here. An image that's larger than the screen is kept at
     * the side of the screen, which is all it can show.
     */
    public static boolean saveFull(File file, byte[] data, int maxSide) {
        File dir = file.getParentFile();
        if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) {
            return false;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        boolean image = bounds.outWidth > 0 && !canAnimate(data);
        if (bounds.outWidth > 0 && !(image ? usableFull(bounds.outWidth, bounds.outHeight)
                : usable(bounds.outWidth, bounds.outHeight))) {
            return false;
        }
        File tmp;
        try {
            tmp = File.createTempFile("motion", ".tmp", dir);
        } catch (IOException e) {
            return false;
        }
        boolean saved = false;
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            if (!image || Math.max(bounds.outWidth, bounds.outHeight) <= maxSide) {
                out.write(data);
            } else if (!shrink(data, bounds, maxSide, out)) {
                return false;
            }
            saved = tmp.renameTo(file);
        } catch (IOException e) {
            return false;
        } finally {
            if (!saved) {
                tmp.delete();
            }
        }
        if (saved) {
            trimMotion(dir, file);
        }
        return saved;
    }

    /**
     * isVideo returns whether a file kept by saveFull is one that images can't read.
     */
    public static boolean isVideo(File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        return bounds.outWidth <= 0;
    }

    /**
     * clear removes all that was kept of tokens' images, as restoring another wallet does.
     */
    public static void clear(Context context) {
        deleteAll(new File(context.getFilesDir(), ICON_DIR));
        deleteAll(new File(context.getCacheDir(), MOTION_DIR));
    }

    private static void deleteAll(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
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
            frame = videoFrame(data, maxSide);
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
        boolean saved = false;
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            if (frame != null) {
                if (!write(frame, maxSide, Bitmap.CompressFormat.JPEG, out)) {
                    return false;
                }
            } else if (Math.max(bounds.outWidth, bounds.outHeight) <= maxSide && data.length <= MAX_KEPT_SIZE) {
                out.write(data);
            } else if (!shrink(data, bounds, maxSide, out)) {
                return false;
            }
            saved = tmp.renameTo(file);
        } catch (IOException e) {
            return false;
        } finally {
            if (!saved) {
                tmp.delete();
            }
        }
        if (saved) {
            VERSION.incrementAndGet();
        }
        return saved;
    }

    /**
     * version returns what changes when an image is saved, whichever screen loaded it.
     */
    public static long version() {
        return VERSION.get();
    }

    // shrink writes the image with its largest side at maxSide, in its proportions.
    private static boolean shrink(byte[] data, BitmapFactory.Options bounds, int maxSide, FileOutputStream out) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        // By the largest side, which is what comes down to maxSide.
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        options.inSampleSize = sampleSize(largest, largest, maxSide);
        Bitmap decoded = BitmapFactory.decodeByteArray(data, 0, data.length, options);
        return decoded != null && write(decoded, maxSide, formatOf(bounds.outMimeType), out);
    }

    // write writes a bitmap with its largest side at most maxSide, in its proportions.
    private static boolean write(Bitmap bitmap, int maxSide, Bitmap.CompressFormat format, FileOutputStream out) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float scale = Math.min(1f, (float) maxSide / Math.max(width, height));
        Bitmap small = Bitmap.createScaledBitmap(bitmap, Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale)), true);
        return small.compress(format, 90, out);
    }

    // formatOf returns the format to keep an image in: its own, as turning sharp art into a JPEG
    // blurs it. A format this device can't write is kept as a PNG, which loses nothing.
    @SuppressWarnings("deprecation")
    private static Bitmap.CompressFormat formatOf(String mimeType) {
        if ("image/jpeg".equals(mimeType)) {
            return Bitmap.CompressFormat.JPEG;
        }
        if ("image/webp".equals(mimeType)) {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP;
        }
        return Bitmap.CompressFormat.PNG;
    }

    /**
     * usable returns whether an image's size is one to decode.
     */
    static boolean usable(int width, int height) {
        return width > 0 && height > 0 && width <= MAX_SIDE && height <= MAX_SIDE;
    }

    /**
     * usableFull returns whether a still image's size is one to bring down to full size.
     */
    static boolean usableFull(int width, int height) {
        return width > 0 && height > 0 && (long) width * height <= MAX_FULL_PIXELS;
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
        return decoded == null ? null : fit(decoded, size);
    }

    // fit draws an image whole, in its proportions, in the middle of a square of the size.
    private static Bitmap fit(Bitmap image, int size) {
        int[] fitted = fitted(image.getWidth(), image.getHeight(), size);
        Bitmap scaled = Bitmap.createScaledBitmap(image, fitted[0], fitted[1], true);
        if (fitted[0] == size && fitted[1] == size) {
            return scaled;
        }
        Bitmap square = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        new Canvas(square).drawBitmap(scaled, (size - fitted[0]) / 2f, (size - fitted[1]) / 2f, null);
        return square;
    }

    /**
     * fitted returns the width and height of an image scaled to fit in a square of the size, in
     * its proportions.
     */
    public static int[] fitted(int width, int height, int size) {
        float scale = (float) size / Math.max(width, height);
        return new int[]{Math.max(1, Math.round(width * scale)), Math.max(1, Math.round(height * scale))};
    }
}
