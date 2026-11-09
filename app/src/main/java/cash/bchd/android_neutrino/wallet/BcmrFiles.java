package cash.bchd.android_neutrino.wallet;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * BcmrFiles puts the files that ship with the app where bchwallet reads them: the hints table of
 * every token, and the Known list. They're copied out of the APK so that a newer table can take
 * the place of the bundled one, and a bundled table replaces any other that isn't whole, newer or
 * the same.
 */
public final class BcmrFiles {

    private static final String MAGIC = "BCMRHNT1";
    private static final int HEIGHT_OFFSET = MAGIC.length();
    // A table's header: the magic, the height, and the numbers of entries and of publications,
    // which take ENTRY_SIZE and PUBLICATION_SIZE bytes each after it.
    private static final int HEADER_SIZE = HEIGHT_OFFSET + 12;
    private static final int ENTRY_SIZE = 72;
    private static final int PUBLICATION_SIZE = 36;

    private BcmrFiles() {
    }

    /**
     * hintsPath returns the path of the hints table of the network, or "" if there's none.
     */
    public static String hintsPath(Context context, Network network) {
        return prepare(context, network.id + "-hints.bin", true);
    }

    /**
     * knownPath returns the path of the Known list of the network, or "" if there's none.
     */
    public static String knownPath(Context context, Network network) {
        return prepare(context, network.id + "-known.json", false);
    }

    private static String prepare(Context context, String name, boolean isHints) {
        File dir = new File(context.getFilesDir(), "bcmr");
        File file = new File(dir, name);
        AssetManager assets = context.getAssets();
        try (InputStream in = assets.open("bcmr/" + name)) {
            byte[] bundled = readAll(in);
            if (!file.exists() || !isHints || !keeps(file, bundled)) {
                write(dir, file, bundled);
            }
            return file.getPath();
        } catch (IOException e) {
            // A wallet works without them, and shows what it can.
            return file.exists() ? file.getPath() : "";
        }
    }

    /**
     * keeps returns whether an installed table stays rather than the bundled one: only one that's
     * whole, and newer, as an update to the table can bring, or of the same height and size, as the
     * bundled one written before is. One that's torn, or of the same height but another, gives way.
     */
    static boolean keeps(File file, byte[] bundled) throws IOException {
        byte[] header = new byte[HEADER_SIZE];
        try (InputStream in = new FileInputStream(file)) {
            if (in.read(header) != header.length) {
                return false;
            }
        }
        if (size(header) != file.length()) {
            return false;
        }
        long installed = height(header), shipped = height(bundled);
        return installed > shipped || installed == shipped && file.length() == bundled.length;
    }

    /**
     * size returns the size a table's header says it has, or -1 if it isn't one.
     */
    static long size(byte[] header) {
        if (header.length < HEADER_SIZE || height(header) < 0) {
            return -1;
        }
        ByteBuffer counts = ByteBuffer.wrap(header, HEIGHT_OFFSET + 4, 8).order(ByteOrder.LITTLE_ENDIAN);
        long entries = counts.getInt() & 0xffffffffL, publications = counts.getInt() & 0xffffffffL;
        return HEADER_SIZE + entries * ENTRY_SIZE + publications * PUBLICATION_SIZE;
    }

    /**
     * height returns the height of a hints table from its header, or -1 if it isn't one.
     */
    static long height(byte[] table) {
        if (table.length < HEIGHT_OFFSET + 4
                || !new String(table, 0, HEIGHT_OFFSET, StandardCharsets.US_ASCII).equals(MAGIC)) {
            return -1;
        }
        return ByteBuffer.wrap(table, HEIGHT_OFFSET, 4).order(ByteOrder.LITTLE_ENDIAN).getInt()
                & 0xffffffffL;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        int n;
        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private static void write(File dir, File file, byte[] data) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("can't make " + dir);
        }
        File tmp = new File(dir, file.getName() + ".tmp");
        // On the disk before it takes the old one's place, so that a crash leaves one or the other.
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(data);
            out.getFD().sync();
        }
        if (!tmp.renameTo(file)) {
            throw new IOException("can't replace " + file);
        }
    }
}
