package io.github.f4iyy.ubc780xlt.ui;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/** Localized texts (English, French). Missing keys show as {@code !key!}. */
public final class Messages {

    private static final String BUNDLE = "i18n.messages";

    private final ResourceBundle bundle;
    private final Locale locale;

    private Messages(ResourceBundle bundle, Locale locale) {
        this.bundle = bundle;
        this.locale = locale;
    }

    /** @param language {@code "fr"} for French, anything else for English */
    public static Messages of(String language) {
        Locale locale = "fr".equals(language) ? Locale.FRENCH : Locale.ENGLISH;
        return new Messages(ResourceBundle.getBundle(BUNDLE, locale, ResourceBundle.Control.getNoFallbackControl(
                ResourceBundle.Control.FORMAT_PROPERTIES)), locale);
    }

    public String get(String key, Object... args) {
        try {
            // Always formatted: apostrophes are doubled in the bundles (MessageFormat rules).
            return new MessageFormat(bundle.getString(key), locale).format(args);
        } catch (MissingResourceException e) {
            return "!" + key + "!";
        }
    }

    public ResourceBundle bundle() {
        return bundle;
    }
}
