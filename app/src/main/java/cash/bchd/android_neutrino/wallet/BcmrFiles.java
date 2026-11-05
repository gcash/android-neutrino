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
 * the place of the bundled one, and a bundled table only replaces an older one.
 */
public final class BcmrFiles {

    private static final String MAGIC = "BCMRHNT1";
    private static final int HEIGHT_OFFSET = MAGIC.length();

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
            if (!file.exists() || !isHints || isNewer(bundled, file)) {
                write(dir, file, bundled);
            }
            return file.getPath();
        } catch (IOException e) {
            // A wallet works without them, and shows what it can.
            return file.exists() ? file.getPath() : "";
        }
    }

    private static boolean isNewer(byte[] bundled, File file) throws IOException {
        byte[] header = new byte[HEIGHT_OFFSET + 4];
        try (InputStream in = new FileInputStream(file)) {
            if (in.read(header) != header.length) {
                return true;
            }
        }
        return height(bundled) > height(header);
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
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(data);
        }
        if (!tmp.renameTo(file)) {
            throw new IOException("can't replace " + file);
        }
    }
}
