package cash.bchd.android_neutrino.tokens;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/**
 * Identicon is the image Cashonize draws for a token from its token ID, so that a token looks
 * the same in both wallets: a 12 by 12 grid, mirrored left to right, of a background color, a
 * pattern color and black spots, all picked by a random number generator seeded with the token
 * ID. It ports Cashonize's src/utils/icons/blockies.ts, called as its
 * TokenIcon.vue calls it (see THIRD_PARTY_NOTICES.md), but square, as the app's images are, where
 * Cashonize draws it in a circle.
 */
public final class Identicon {

    static final int GRID = 12;

    // What a cell of the grid shows.
    static final int BACKGROUND = 0;
    static final int PATTERN = 1;
    static final int SPOT = 2;

    // The colors the generator picked, as hue in degrees and saturation and lightness in
    // percent, the numbers Cashonize writes into its hsl() colors.
    final double[] backgroundHsl;
    final double[] patternHsl;
    // The cells, row by row.
    final int[] cells;

    private Identicon(double[] backgroundHsl, double[] patternHsl, int[] cells) {
        this.backgroundHsl = backgroundHsl;
        this.patternHsl = patternHsl;
        this.cells = cells;
    }

    /**
     * of makes the identicon of a token ID, in hex in the byte order of transaction IDs, as
     * Cashonize has token IDs.
     */
    public static Identicon of(String tokenId) {
        Random random = new Random(tokenId);
        // Cashonize gives the black of the spots, so the generator picks the other two colors,
        // the pattern's first.
        double[] pattern = random.color();
        double[] background = random.color();
        int[] cells = new int[GRID * GRID];
        int half = (GRID + 1) / 2;
        for (int y = 0; y < GRID; y++) {
            for (int x = 0; x < half; x++) {
                int cell = (int) Math.floor(random.next() * 2.3);
                cells[y * GRID + x] = cell;
                cells[y * GRID + GRID - 1 - x] = cell;
            }
        }
        return new Identicon(background, pattern, cells);
    }

    /**
     * bitmap draws the identicon sizePx across, as Cashonize scales a grid of whole
     * pixels per cell down to the icon's size.
     */
    public Bitmap bitmap(int sizePx) {
        int cell = (sizePx + GRID - 1) / GRID;
        Bitmap grid = Bitmap.createBitmap(GRID * cell, GRID * cell, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(grid);
        canvas.drawColor(argb(backgroundHsl));
        Paint paint = new Paint();
        int pattern = argb(patternHsl);
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] == BACKGROUND) {
                continue;
            }
            paint.setColor(cells[i] == PATTERN ? pattern : Color.BLACK);
            int left = i % GRID * cell;
            int top = i / GRID * cell;
            canvas.drawRect(left, top, left + cell, top + cell, paint);
        }

        return Bitmap.createScaledBitmap(grid, sizePx, sizePx, true);
    }

    /**
     * argb converts a color in hue, saturation and lightness to ARGB, as CSS converts an hsl()
     * color, with each channel rounded to the nearest of its 256 levels.
     */
    static int argb(double[] hsl) {
        double hue = hsl[0] % 360;
        double lightness = hsl[2] / 100;
        double a = hsl[1] / 100 * Math.min(lightness, 1 - lightness);
        return 0xff000000 | channel(0, hue, lightness, a) << 16
                | channel(8, hue, lightness, a) << 8 | channel(4, hue, lightness, a);
    }

    private static int channel(int n, double hue, double lightness, double a) {
        double k = (n + hue / 30) % 12;
        double value = lightness - a * Math.max(-1, Math.min(Math.min(k - 3, 9 - k), 1));
        return (int) Math.round(value * 255);
    }

    /**
     * Random is blockies' xorshift generator, seeded from the characters of the identifier.
     * JavaScript shifts and XORs a number as the 32-bit integer it equals modulo 2^32, so Java's
     * int, which wraps modulo 2^32, gives the same numbers. They are in [0, 1), not [0, 2) as
     * the division suggests, since each step XORs two values with their own arithmetic shifts,
     * whose sign bits cancel.
     */
    private static final class Random {
        private final int[] seed = new int[4];

        Random(String identifier) {
            for (int i = 0; i < identifier.length(); i++) {
                int j = i % 4;
                seed[j] = (seed[j] << 5) - seed[j] + identifier.charAt(i);
            }
        }

        double next() {
            int t = seed[0] ^ (seed[0] << 11);
            seed[0] = seed[1];
            seed[1] = seed[2];
            seed[2] = seed[3];
            seed[3] = seed[3] ^ (seed[3] >> 19) ^ t ^ (t >> 8);
            return (seed[3] & 0xffffffffL) / (double) (1L << 31);
        }

        // color picks a color as blockies' createColor does: the hue in whole degrees, the
        // saturation from 40%, and the lightness from four numbers.
        double[] color() {
            double hue = Math.floor(next() * 360);
            double saturation = next() * 60 + 40;
            double lightness = (next() + next() + next() + next()) * 25;
            return new double[]{hue, saturation, lightness};
        }
    }
}
