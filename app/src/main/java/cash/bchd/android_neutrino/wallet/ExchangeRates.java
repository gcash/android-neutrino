package cash.bchd.android_neutrino.wallet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Currency;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ExchangeRates fetches the price of BCH in fiat currencies from BitPay, or from Coinbase when
 * BitPay is unavailable, and refreshes it every five minutes.
 */
public class ExchangeRates {
    private static ExchangeRates instance;
    private static final String BITPAY_ENDPOINT = "https://bitpay.com/rates/BCH";
    private static final String COINBASE_ENDPOINT = "https://api.coinbase.com/v2/exchange-rates?currency=BCH";
    private static final int TIMEOUT_MILLIS = 15000;

    // The price of one BCH in each fiat currency, by ISO 4217 code.
    private volatile Map<String, Double> rates;

    public ExchangeRates() {
        instance = this;
        startExchangeRateService();
    }

    public static ExchangeRates getInstance() {
        return instance;
    }

    private void startExchangeRateService() {
        Runnable service = new Runnable() {
            @Override
            public void run() {
                try {
                    rates = fetchRates();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(service, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * Fetch the price of one BCH in each fiat currency.
     */
    static Map<String, Double> fetchRates() throws IOException, JSONException {
        try {
            return parseBitPayRates(readJsonFromUrl(BITPAY_ENDPOINT));
        } catch (IOException | JSONException e) {
            return parseCoinbaseRates(readJsonFromUrl(COINBASE_ENDPOINT));
        }
    }

    /**
     * Parse BitPay's rates, {"data":[{"code":"USD","name":"US Dollar","rate":271.31},...]}.
     */
    static Map<String, Double> parseBitPayRates(JSONObject json) throws JSONException {
        JSONArray data = json.getJSONArray("data");
        Map<String, Double> rates = new HashMap<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject rate = data.getJSONObject(i);
            rates.put(rate.getString("code"), rate.getDouble("rate"));
        }
        return rates;
    }

    /**
     * Parse Coinbase's rates, {"data":{"currency":"BCH","rates":{"USD":"274.07",...}}}.
     */
    static Map<String, Double> parseCoinbaseRates(JSONObject json) throws JSONException {
        JSONObject data = json.getJSONObject("data").getJSONObject("rates");
        Map<String, Double> rates = new HashMap<>();
        Iterator<String> codes = data.keys();
        while (codes.hasNext()) {
            String code = codes.next();
            try {
                rates.put(code, Double.parseDouble(data.getString(code)));
            } catch (NumberFormatException e) {
                // Leave out a rate that is not a number.
            }
        }
        return rates;
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
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        connection.setRequestProperty("Accept", "application/json");
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException(url + " returned HTTP " + connection.getResponseCode());
            }
            try (InputStream is = connection.getInputStream()) {
                BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                return new JSONObject(readAll(rd));
            }
        } finally {
            connection.disconnect();
        }
    }

    // The price of one BCH in the currency.
    private double rate(Currency currencyCode) throws Exception {
        Map<String, Double> current = rates;
        if (current == null) {
            throw new Exception("The exchange rates are not loaded yet");
        }
        Double rate = current.get(currencyCode.getCurrencyCode());
        if (rate == null || rate <= 0) {
            throw new Exception("There is no exchange rate for " + currencyCode.getCurrencyCode());
        }
        return rate;
    }

    public String getFormattedAmountInFiat(Amount bchAmount, Currency currencyCode) throws Exception {
        double fiat = round(rate(currencyCode) * bchAmount.toBCH(), 2);
        String fiatStr = String.valueOf(fiat);
        if (fiatStr.indexOf(".") != 0) {
            String afterDecimal = fiatStr.substring(fiatStr.indexOf(".")+1);
            if (afterDecimal.length() == 1) {
                fiatStr = fiatStr + "0";
            }
        }
        String s = String.format("%.12f", 12.345);
        if (s.contains(",")) {
            fiatStr = fiatStr.replace(".", ",");
        }
        return currencyCode.getSymbol() + fiatStr;
    }

    public double convertToBCH(double amount, Currency currencyCode) throws Exception {
        return round(amount / rate(currencyCode), 8);
    }

    public void fetchFormattedAmountInFiat(Amount bchAmount, Currency currencyCode, Callback cb) throws Exception {
        rates = fetchRates();
        cb.onRateFetched(getFormattedAmountInFiat(bchAmount, currencyCode));
    }

    private static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }

    public static class Callback {
        public void onRateFetched(String formatted) {}
    }
}
