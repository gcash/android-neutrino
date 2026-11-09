package cash.bchd.android_neutrino.wallet;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import cash.bchd.android_neutrino.tokens.TokenLabels;
import walletrpc.Api;

/**
 * TokenLabelFile keeps what the wallet said of tokens between runs, a file for each network, so that
 * the first screen shows names, real amounts and warnings rather than waiting for the wallet. What
 * it says when it's up replaces this. It's only a cache: a file that can't be read leaves nothing.
 */
public final class TokenLabelFile {

    private static final long SAVE_DELAY_SECONDS = 3;
    // Far more than a wallet's tokens take, as it's read before the first screen shows.
    private static final long MAX_FILE_SIZE = 4 << 20;
    private static final ScheduledExecutorService SAVER = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "token-labels");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile Network network;
    private static File file;
    private static boolean saveDue;

    private TokenLabelFile() {
    }

    /**
     * open makes the labels those of the network, and does nothing if they already are. What the
     * wallet said stays over a restart, which screens stay open across.
     */
    public static void open(Context context, Network now) {
        // Without the lock, as a save can hold it, and a screen is waiting.
        if (now == network) {
            return;
        }
        synchronized (TokenLabelFile.class) {
            TokenLabels.clear();
            network = now;
            file = new File(context.getFilesDir(), fileName(now));
            TokenLabels.onChange(TokenLabelFile::saveSoon);
            // Off the main thread, and on the thread that saves, so that no save comes before it.
            // What the wallet said meanwhile stays over it.
            File reading = file;
            SAVER.execute(() -> {
                List<TokenMeta> tokens = read(reading);
                synchronized (TokenLabelFile.class) {
                    if (network == now) {
                        TokenLabels.load(tokens);
                    }
                }
            });
        }
    }

    /**
     * delete removes what's kept of a network's tokens, as restoring a wallet does.
     */
    public static synchronized void delete(Context context, Network network) {
        context.deleteFile(fileName(network));
        context.deleteFile(fileName(network) + ".tmp");
    }

    static String fileName(Network network) {
        return "tokenLabels_" + network.id + ".bin";
    }

    // saveSoon saves once for a run of changes, as a wallet's replies come in parts.
    private static synchronized void saveSoon() {
        if (!saveDue) {
            saveDue = true;
            SAVER.schedule(TokenLabelFile::save, SAVE_DELAY_SECONDS, TimeUnit.SECONDS);
        }
    }

    private static synchronized void save() {
        saveDue = false;
        try {
            write(file, TokenLabels.all());
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
        }
    }

    /**
     * write replaces the file whole, so that the app being killed can't leave half of one.
     */
    static void write(File file, List<TokenMeta> tokens) throws IOException {
        Api.TokenMetadataResponse.Builder all = Api.TokenMetadataResponse.newBuilder();
        for (TokenMeta token : tokens) {
            all.addTokens(token.kept());
        }
        File tmp = new File(file.getPath() + ".tmp");
        boolean saved = false;
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            all.build().writeTo(out);
            out.getFD().sync();
            saved = tmp.renameTo(file);
            if (!saved) {
                throw new IOException("Unable to save the token labels");
            }
        } finally {
            if (!saved) {
                tmp.delete();
            }
        }
    }

    /**
     * read returns what's in the file, or nothing if it's missing or can't be read. It's read as
     * the app starts, so a file too large for that goes, as it would stop the app every time.
     */
    static List<TokenMeta> read(File file) {
        if (!file.exists()) {
            return Collections.emptyList();
        }
        if (file.length() > MAX_FILE_SIZE) {
            file.delete();
            return Collections.emptyList();
        }
        try {
            List<TokenMeta> tokens = new ArrayList<>();
            byte[] data = Files.readAllBytes(file.toPath());
            for (Api.TokenMetadataResponse.Token token : Api.TokenMetadataResponse.parseFrom(data).getTokensList()) {
                tokens.add(new TokenMeta(token));
            }
            return tokens;
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }
}
