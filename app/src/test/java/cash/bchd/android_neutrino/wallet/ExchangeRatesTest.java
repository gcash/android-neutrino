package cash.bchd.android_neutrino.wallet;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ExchangeRatesTest {

    @Test
    public void parsesBitPayRates() throws Exception {
        JSONObject json = new JSONObject("{\"data\":[{\"code\":\"BCH\",\"name\":\"Bitcoin Cash\",\"rate\":1},"
                + "{\"code\":\"USD\",\"name\":\"US Dollar\",\"rate\":271.31},"
                + "{\"code\":\"EUR\",\"name\":\"Eurozone Euro\",\"rate\":242.35}]}");
        Map<String, Double> rates = ExchangeRates.parseBitPayRates(json);
        assertEquals(271.31, rates.get("USD"), 0);
        assertEquals(242.35, rates.get("EUR"), 0);
        assertEquals(3, rates.size());
    }

    @Test
    public void parsesCoinbaseRates() throws Exception {
        JSONObject json = new JSONObject("{\"data\":{\"currency\":\"BCH\",\"rates\":"
                + "{\"USD\":\"274.07\",\"EUR\":\"244.94\",\"JPY\":\"42992.2591139337\",\"BAD\":\"n/a\"}}}");
        Map<String, Double> rates = ExchangeRates.parseCoinbaseRates(json);
        assertEquals(274.07, rates.get("USD"), 0);
        assertEquals(42992.2591139337, rates.get("JPY"), 0);
        assertFalse(rates.containsKey("BAD"));
    }
}
