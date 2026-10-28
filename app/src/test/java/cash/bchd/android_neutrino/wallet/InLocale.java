package cash.bchd.android_neutrino.wallet;

import java.util.Locale;

/**
 * InLocale runs test code with the default locale of a language, as the phone's language sets it.
 */
final class InLocale {

    private InLocale() {
    }

    static void run(String languageTag, Runnable code) {
        Locale saved = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag(languageTag));
        try {
            code.run();
        } finally {
            Locale.setDefault(saved);
        }
    }
}
