package cash.bchd.android_neutrino.tokens;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.RectF;
import android.graphics.drawable.AnimatedImageDrawable;
import android.media.MediaDataSource;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.widget.ImageView;

import androidx.annotation.VisibleForTesting;

import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;

import java.io.ByteArrayInputStream;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    // The largest side of art that moves, whose every frame takes memory as it plays.
    private static final int MAX_MOTION_SIDE = 2048;
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

    // The images drawn so far, each under a key that starts with its token ID, up to an eighth of
    // the memory the app may use, as every screen shows the same few and drawing them is slow.
    private static final DrawnImages<Bitmap> DRAWN = new DrawnImages<>(Runtime.getRuntime().maxMemory() / 8,
            Bitmap::getByteCount);
    // Draws the images off the main thread.
    private static final ExecutorService DRAWER = Executors.newFixedThreadPool(2);

    /**
     * Drawing draws an image, off the main thread.
     */
    private interface Drawing {
        Bitmap draw();
    }

    private TokenIcons() {
    }

    private static File file(Context context, String category) {
        return new File(new File(context.getFilesDir(), ICON_DIR), category);
    }

    // nftName returns the name of an NFT's files: its token ID and commitment, with a long
    // commitment hashed, as a file's name can't be as long as a commitment can, and marked so,
    // with a letter no commitment's hex has, so that no hash reads as another's commitment.
    static String nftName(String category, String commitment) {
        if (commitment.length() <= MAX_NAME_COMMITMENT) {
            return category + "-" + commitment;
        }
        return category + "-h" + sha256(commitment);
    }

    // sha256 returns the SHA-256 of the text, in all of its 64 hex digits.
    static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return String.format("%064x", new BigInteger(1, digest));
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
        Set<String> before = TokenIcons.chosen;
        boolean allBefore = showAll;
        showAll = all;
        TokenIcons.chosen = new HashSet<>(chosen);
        // What was drawn of the tokens whose images now show or no longer do is wrong.
        if (all != allBefore) {
            DRAWN.clear();
            return;
        }
        Set<String> changed = new HashSet<>(before);
        changed.addAll(chosen);
        Set<String> kept = new HashSet<>(before);
        kept.retainAll(chosen);
        changed.removeAll(kept);
        for (String category : changed) {
            forgetDrawn(category);
        }
    }

    /**
     * forgetDrawn drops what was drawn of a token's images, as they changed, or as what shows of
     * them did, as when the wallet said more of it.
     */
    public static void forgetDrawn(String category) {
        DRAWN.removeStartingWith(category);
    }

    /**
     * generation returns how many times what was drawn of a token was let go, for tests.
     */
    @VisibleForTesting
    public static long generation(String category) {
        return DRAWN.generations(category)[1];
    }

    // nftScope returns the scope of what's drawn of an NFT's art, at any size.
    private static String nftScope(String category, String commitment) {
        return category + ":nft:" + commitment + ":";
    }

    /**
     * showIcon puts a token's icon on a view, as bitmap draws it, off the main thread unless it was
     * drawn before. Until it's drawn, the view shows the token's identicon.
     */
    public static void showIcon(ImageView view, String category, int size) {
        Context context = view.getContext().getApplicationContext();
        show(view, category + ":icon:" + size, category, size, () -> bitmap(context, category, size), category);
    }

    /**
     * showNft puts an NFT's art on a view, as nftBitmap draws it, as showIcon does a token's icon.
     */
    public static void showNft(ImageView view, String category, String commitment, int size) {
        Context context = view.getContext().getApplicationContext();
        String scope = nftScope(category, commitment);
        show(view, scope + size, category, size, () -> nftBitmap(context, category, commitment, size), category, scope);
    }

    /**
     * showListIcon puts a token's icon on a view where no badge shows, as in a transaction's row:
     * only a trusted token's, and else its identicon, as an icon can be made to read as another
     * token's.
     */
    public static void showListIcon(ImageView view, String category, int size) {
        if (TokenLabels.trusted(category)) {
            showIcon(view, category, size);
        } else {
            showIdenticon(view, category, size);
        }
    }

    /**
     * showListNft puts an NFT's art on a view where no badge shows, as showListIcon does a token's
     * icon.
     */
    public static void showListNft(ImageView view, String category, String commitment, int size) {
        if (TokenLabels.trusted(category)) {
            showNft(view, category, commitment, size);
        } else {
            showIdenticon(view, category, size);
        }
    }

    /**
     * showIdenticon puts a token's identicon on a view, which nothing drawn before replaces.
     */
    public static void showIdenticon(ImageView view, String category, int size) {
        view.setTag(R.id.image_key, null);
        view.setImageBitmap(identicon(category, size));
    }

    // identicon returns a token's identicon, drawn once.
    private static Bitmap identicon(String category, int size) {
        String key = category + ":identicon:" + size;
        Bitmap identicon = DRAWN.get(key);
        if (identicon == null) {
            identicon = Identicon.of(category).bitmap(size);
            DRAWN.put(key, identicon);
        }
        return identicon;
    }

    /**
     * clear has a view that showed a token's image show something else, which nothing drawn before
     * replaces.
     */
    public static void clear(ImageView view) {
        view.setTag(R.id.image_key, null);
    }

    // show puts the image of the key on the view, drawn at once if it was before, and else off the
    // main thread, the identicon showing until it is. A view shows only the image it was last given.
    // What a scope's change made wrong while it was drawn is drawn again, if the view still wants it.
    private static void show(ImageView view, String key, String category, int size, Drawing drawing,
                             String... scopes) {
        view.setTag(R.id.image_key, key);
        Bitmap drawn = DRAWN.get(key);
        if (drawn != null) {
            view.setImageBitmap(drawn);
            return;
        }
        view.setImageBitmap(identicon(category, size));
        long[] seen = DRAWN.generations(scopes);
        DRAWER.execute(() -> {
            Bitmap image;
            try {
                image = drawing.draw();
            } catch (RuntimeException | OutOfMemoryError e) {
                e.printStackTrace();
                return;
            }
            if (image == null) {
                return;
            }
            boolean current = DRAWN.putIfCurrent(key, image, scopes, seen);
            view.post(() -> {
                if (!key.equals(view.getTag(R.id.image_key))) {
                    return;
                }
                if (current) {
                    view.setImageBitmap(image);
                } else {
                    show(view, key, category, size, drawing, scopes);
                }
            });
        });
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
     * the user loaded them from the token's menu. A look-alike's never do, whatever was saved of
     * them before the wallet said so, as they're made to mislead.
     */
    public static boolean showing(String category) {
        return (showAll || chosen.contains(category)) && !TokenLabels.isLookAlike(category);
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
     * save keeps an icon if it's an image this device can show, with the URI it came from, and
     * returns whether it did.
     */
    public static boolean save(Context context, String category, byte[] data, String uri) {
        boolean saved = save(file(context, category), data, largestShown(context));
        if (saved) {
            try {
                Files.write(iconSourceFile(context, category).toPath(), uri.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                e.printStackTrace();
            }
            forgetDrawn(category);
        }
        return saved;
    }

    // iconSourceFile returns the file that says what URI a token's icon was saved from.
    private static File iconSourceFile(Context context, String category) {
        return new File(file(context, category).getPath() + ".source");
    }

    /**
     * iconSavedFrom returns whether a token's saved icon came from the URI. One saved by earlier
     * versions says nothing, and a registry can come to name another icon.
     */
    public static boolean iconSavedFrom(Context context, String category, String uri) {
        try {
            byte[] saved = Files.readAllBytes(iconSourceFile(context, category).toPath());
            return has(context, category) && uri.equals(new String(saved, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return false;
        }
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
        } else if (saved) {
            // Still art that took the place of art that moved doesn't.
            motionMarker(context, category, commitment).delete();
        }
        if (saved) {
            // Only this NFT's, as the others' art and the icon are as they were.
            DRAWN.removeStartingWith(nftScope(category, commitment));
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

    // moves returns whether a file is a video, which no image decoder reads, but isn't an SVG,
    // which none reads either, or an animation. The file is a stranger's, so an image's size is
    // checked first, and an animation is only looked at as a thumbnail.
    private static boolean moves(byte[] data) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        if (bounds.outWidth <= 0) {
            return !looksLikeSvg(data);
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
        String name = nftName(category, commitment) + "-" + sha256(uri);
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
        // An SVG is drawn at the side to show, as it's shown as an image.
        Bitmap drawn = bounds.outWidth <= 0 ? svg(data, maxSide) : null;
        if (drawn != null) {
            return saveDrawn(file, dir, drawn, maxSide);
        }
        boolean image = bounds.outWidth > 0 && !canAnimate(data);
        if (bounds.outWidth > 0 && !(image ? usableFull(bounds.outWidth, bounds.outHeight)
                : usableMotion(bounds.outWidth, bounds.outHeight))) {
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
     * forget removes all that was kept of a token's images, its icon and its NFTs' art, as for one
     * the user hid, or a restore. Each of its files' names starts with its token ID.
     */
    public static void forget(Context context, String category) {
        forget(category, new File(context.getFilesDir(), ICON_DIR), new File(context.getCacheDir(), MOTION_DIR));
        forgetDrawn(category);
        VERSION.incrementAndGet();
    }

    static void forget(String category, File... dirs) {
        for (File dir : dirs) {
            File[] files = dir.listFiles((d, name) -> name.startsWith(category));
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
        }
    }

    static void trimMotion(File dir, File keep) {
        // Not another save's file still being written, which would end that save.
        File[] files = dir.listFiles((d, name) -> !name.endsWith(".tmp"));
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

    // saveDrawn keeps an image drawn for full art as a PNG, as saveFull keeps the others.
    private static boolean saveDrawn(File file, File dir, Bitmap drawn, int maxSide) {
        File tmp;
        try {
            tmp = File.createTempFile("motion", ".tmp", dir);
        } catch (IOException e) {
            return false;
        }
        boolean saved = false;
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            saved = write(drawn, maxSide, Bitmap.CompressFormat.PNG, out) && tmp.renameTo(file);
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
     * looksLikeSvg returns whether the file starts as an SVG does, as Android can't tell.
     */
    static boolean looksLikeSvg(byte[] data) {
        String start = new String(data, 0, Math.min(data.length, 4096), StandardCharsets.UTF_8).stripLeading();
        if (start.startsWith("\uFEFF")) {
            start = start.substring(1).stripLeading();
        }
        return (start.startsWith("<svg") || start.startsWith("<?xml") || start.startsWith("<!--")
                || start.startsWith("<!DOCTYPE")) && start.contains("<svg");
    }

    // The most an SVG is let be, as it's drawn by a stranger's instructions, which can make it take
    // any time or memory: its size, and the number of the elements that draw others again.
    private static final int MAX_SVG_SIZE = 256 << 10;
    private static final int MAX_SVG_USES = 64;

    /**
     * cheapSvg returns whether an SVG is one to draw: small, with no entities, which can grow
     * without limit, no data: images, which decode at any size, and few elements that draw others.
     */
    static boolean cheapSvg(byte[] data) {
        if (data.length > MAX_SVG_SIZE) {
            return false;
        }
        String text = new String(data, StandardCharsets.UTF_8);
        if (text.contains("<!ENTITY") || text.contains("data:")) {
            return false;
        }
        int uses = 0;
        for (int i = text.indexOf("<use"); i >= 0; i = text.indexOf("<use", i + 4)) {
            if (++uses > MAX_SVG_USES) {
                return false;
            }
        }
        return true;
    }

    // svg draws an SVG at up to the side, in its proportions, or returns null if it isn't one
    // or can't be drawn. Nothing it names elsewhere is fetched, as no resolver is set for it.
    private static Bitmap svg(byte[] data, int maxSide) {
        if (!looksLikeSvg(data) || !cheapSvg(data)) {
            return null;
        }
        try {
            SVG svg = SVG.getFromInputStream(new ByteArrayInputStream(data));
            RectF box = svg.getDocumentViewBox();
            float width = box != null ? box.width() : svg.getDocumentWidth();
            float height = box != null ? box.height() : svg.getDocumentHeight();
            if (!(width > 0) || !(height > 0)) {
                width = height = maxSide;
            }
            if (box == null) {
                svg.setDocumentViewBox(0, 0, width, height);
            }
            int[] fitted = fitted(Math.round(width), Math.round(height), maxSide);
            Bitmap image = Bitmap.createBitmap(fitted[0], fitted[1], Bitmap.Config.ARGB_8888);
            // At the size drawn, as a width and height of its own would draw it at those.
            svg.setDocumentWidth(fitted[0]);
            svg.setDocumentHeight(fitted[1]);
            svg.renderToCanvas(new Canvas(image));
            return image;
        } catch (SVGParseException | RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    // largestShown returns the largest side any screen shows an image at on this device, which is
    // all that's worth keeping, as a collection's art can be megabytes each.
    private static int largestShown(Context context) {
        return context.getResources().getDimensionPixelSize(R.dimen.nft_screen_icon);
    }

    private static boolean save(File file, byte[] data, int maxSide) {
        // A video keeps its first frame, as lists show images, and an SVG is drawn, as Android
        // can't, and kept as a PNG, which keeps what it leaves clear.
        Bitmap frame = null;
        Bitmap.CompressFormat format = Bitmap.CompressFormat.JPEG;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        if (bounds.outWidth <= 0) {
            frame = svg(data, maxSide);
            if (frame != null) {
                format = Bitmap.CompressFormat.PNG;
            } else {
                frame = videoFrame(data, maxSide);
            }
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
                if (!write(frame, maxSide, format, out)) {
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
     * usableMotion returns whether art that moves is small enough to play on a phone with little
     * memory.
     */
    static boolean usableMotion(int width, int height) {
        return width > 0 && height > 0 && width <= MAX_MOTION_SIDE && height <= MAX_MOTION_SIDE;
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
