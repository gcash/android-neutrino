package cash.bchd.android_neutrino.wallet;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Currency;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ExchangeRates {
    private static final String EXCHANGE_RATE_ENDPOINT = "https://ticker.openbazaar.org/api";

    private JSONObject rates;

    public ExchangeRates() {
        startExchangeRateService();
    }

    private void startExchangeRateService() {
        Runnable service = new Runnable() {
            @Override
            public void run() {
                try {
                    rates = readJsonFromUrl(EXCHANGE_RATE_ENDPOINT);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(service, 0, 5, TimeUnit.MINUTES);
    }

    private static String readAll(Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
    }

    public static JSONObject readJsonFromUrl(String url) throws IOException, JSONException {
        InputStream is = new URL(url).openStream();
        try {
            BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
            String jsonText = readAll(rd);
            JSONObject json = new JSONObject(jsonText);
            return json;
        } finally {
            is.close();
        }
    }

    public String getFormattedAmountInFiat(Amount bchAmount, Currency currencyCode) throws Exception {
        JSONObject btcObj = rates.getJSONObject(currencyCode.getCurrencyCode());
        double btcRate = btcObj.getDouble("last");

        JSONObject bchObj = rates.getJSONObject("BCH");
        double bchBtcRate = bchObj.getDouble("last");

        double fiat = round((btcRate/bchBtcRate) * bchAmount.toBCH(), 2);
        return currencyCode.getSymbol() + String.valueOf(fiat);
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }
}
