package cash.bchd.android_neutrino.tokens;

import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class IdenticonTest {

    // What Cashonize's own blockies.ts drew for these token IDs, as its TokenIcon.vue calls it,
    // with a canvas that recorded the colors it set and the cells it filled, row by row: 0 for
    // the background, 1 for the pattern color and 2 for a black spot. The first eight are the
    // test tokens on testnet4.
    private static final String[][] CASHONIZE = {
            {"da3598d398c438a8d579cbe4139363447331b631d1cc01c3d02dbf9105e3aecf",
                    "hsl(207,52.111197244375944%,41.57602096674964%)", "hsl(97,94.41956808790565%,54.013132804539055%)",
                    "001101101100001001100100000121121000121221122121110000000011011120021110010101101010100000000001121001100121121010010121100012210001112110011211"},
            {"85155463c830986b922eff31c115206e799503d3fbfe0d7e06ea7e7451e0c19a",
                    "hsl(39,76.14161974750459%,45.05679983412847%)", "hsl(172,91.79383208975196%,53.31219637300819%)",
                    "102101101201010011110010001000000100102010010201102001100201100101101001010011110010001202202100001110011100000110011000112001100211011100001110"},
            {"fcbd5a36b64e7bc670dc43915cd18d741836e63b9705d432975be914f1cbb974",
                    "hsl(19,49.49350315146148%,43.91753301024437%)", "hsl(179,63.10686786659062%,68.35365404840559%)",
                    "210111111012200020020002021010010120011000000110110101101011111011110111001100001100200000000002020100001020100011110001001211112100011010010110"},
            {"b1d29053024c4ee908f7cbf170677e8a13928bbe5cc909fcb546e404ec7848dd",
                    "hsl(278,44.11702800542116%,44.59129486931488%)", "hsl(235,59.65017717331648%,47.62152113253251%)",
                    "112111111211201010010102201102201102201100001102000101101000011102201110110101101011010110011010112110011211110101101011002100001200112101101211"},
            {"76c229dd545d1867005083730568cfb97f5673d7f6fbca064ea677e00f361e13",
                    "hsl(208,79.15383490733802%,32.58017972111702%)", "hsl(202,93.44092758372426%,50.967027503065765%)",
                    "020101101020011112211110001001100100100011110001021011110120120020020021210111111012010011110010100011110001001201102100020121121020200110011002"},
            {"1f02509e0be412e1c13e3229b22296cabfd998e424a72e05cffb74f8f6092a2c",
                    "hsl(150,55.21987268701196%,37.90448805084452%)", "hsl(159,97.73350513540208%,55.89287864277139%)",
                    "010212212010111100001111011010010110011011110110110100001011110100001011100010010001001001100100101112211101120010010021011001100110211020020112"},
            {"0e6867a305b9c9d4d369fc850b012c4e8f531f214e36936b8c7822b7039c3aa0",
                    "hsl(7,96.0670268535614%,32.32474513351917%)", "hsl(308,44.13126575760543%,67.54510394530371%)",
                    "201210012102020221122020020111111020110021120011020001100020011012210110100121121001000011110000101011110101211022220112010102201010110000000011"},
            {"997ead9d96bd9e72f090fd0578d324313b7d2978e87fcb65b95313f7e2841dff",
                    "hsl(305,52.39268868230283%,63.05655168835074%)", "hsl(7,94.72401862032712%,70.98966897465289%)",
                    "011001100110011110011110020001100020210100001012011102201110100001100001100110011001000100001000211010010112000100001000201111111102010101101010"},
            {"0000000000000000000000000000000000000000000000000000000000000000",
                    "hsl(219,51.502341916784644%,41.295261250343174%)", "hsl(219,51.50226474739611%,40.16843276331201%)",
                    "200211112002112111111211110101101011022110011220000111111000212100001212100101101001201010010102020100001020010020020010000210012000010212212010"},
            {"ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                    "hsl(108,79.44247680716217%,57.30975429760292%)", "hsl(108,79.44246057420969%,48.40516257099807%)",
                    "001121121100100122221001010001100010100000000001112010010211200000000002121100001121021111111120002002200200111110011111111122221111021201102120"},
    };

    private static double[] hsl(String css) {
        Matcher m = Pattern.compile("hsl\\(([0-9.]+),([0-9.]+)%,([0-9.]+)%\\)").matcher(css);
        if (!m.matches()) {
            throw new IllegalArgumentException(css);
        }
        return new double[]{Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)),
                Double.parseDouble(m.group(3))};
    }

    @Test
    public void drawsWhatCashonizeDraws() {
        for (String[] c : CASHONIZE) {
            Identicon icon = Identicon.of(c[0]);
            // JavaScript prints the shortest decimal that reads back as the same double, so the
            // colors match to the last bit.
            assertArrayEquals(c[0], hsl(c[1]), icon.backgroundHsl, 0);
            assertArrayEquals(c[0], hsl(c[2]), icon.patternHsl, 0);
            StringBuilder cells = new StringBuilder();
            for (int cell : icon.cells) {
                cells.append(cell);
            }
            assertEquals(c[0], c[3], cells.toString());
        }
    }

    @Test
    public void mirrorsEachRow() {
        Identicon icon = Identicon.of(CASHONIZE[0][0]);
        for (int y = 0; y < Identicon.GRID; y++) {
            for (int x = 0; x < Identicon.GRID; x++) {
                assertEquals(icon.cells[y * Identicon.GRID + x],
                        icon.cells[y * Identicon.GRID + Identicon.GRID - 1 - x]);
            }
        }
    }

    @Test
    public void convertsColorsAsCss() {
        assertEquals(0xffff0000, Identicon.argb(new double[]{0, 100, 50}));
        assertEquals(0xff008000, Identicon.argb(new double[]{120, 100, 25}));
        assertEquals(0xff0000ff, Identicon.argb(new double[]{240, 100, 50}));
        assertEquals(0xffffffff, Identicon.argb(new double[]{0, 0, 100}));
        assertEquals(0xff000000, Identicon.argb(new double[]{0, 0, 0}));
        // Two of Cashonize's colors, converted with Python's colorsys.
        assertEquals(0xff3370a1, Identicon.argb(hsl(CASHONIZE[0][1])));
        assertEquals(0xff70f81b, Identicon.argb(hsl(CASHONIZE[0][2])));
    }
}
