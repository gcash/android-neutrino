package cash.bchd.android_neutrino;

import org.junit.Test;

import java.math.BigDecimal;

import cash.bchd.android_neutrino.wallet.Amount;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() {
        System.out.println(removeTrailingZeros(123.456));
    }

    private static String removeTrailingZeros(double d) {
        String s = String.format("%.12f", d);
        s = s.replace(",", ".");
        BigDecimal stripedVal = new BigDecimal(s).stripTrailingZeros();
        if (stripedVal.toString().contains("E")) {
            return String.format("%.0f", stripedVal);
        }
        return stripedVal.toString();
    }

}