package com.churchadmin.services;

import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

/**
 * Manages the active application locale and persists it across sessions.
 *
 * Supported locales (ADR §5.4):
 *   - English  (en)  — default, LTR
 *   - Dutch    (nl)  — full translation, LTR
 *   - Arabic   (ar)  — full translation, RTL
 *
 * JavaFX controllers observe this service and reload their ResourceBundle
 * when the language is changed in Settings, without restarting.
 */
@Service
public class LocaleService {

    private static final String PREF_KEY = "app.locale";
    private static final String BUNDLE_BASE = "i18n/messages";

    public static final Locale EN = Locale.ENGLISH;
    public static final Locale NL = Locale.forLanguageTag("nl");
    public static final Locale AR = Locale.forLanguageTag("ar");

    public static final List<Locale> SUPPORTED_LOCALES = List.of(EN, NL, AR);

    private final Preferences prefs = Preferences.userNodeForPackage(LocaleService.class);

    @Getter
    private Locale currentLocale;

    private ResourceBundle currentBundle;

    public LocaleService() {
        String savedTag = prefs.get(PREF_KEY, EN.toLanguageTag());
        currentLocale = Locale.forLanguageTag(savedTag);
        loadBundle();
    }

    public void setLocale(Locale locale) {
        if (!SUPPORTED_LOCALES.contains(locale)) {
            throw new IllegalArgumentException("Unsupported locale: " + locale);
        }
        currentLocale = locale;
        prefs.put(PREF_KEY, locale.toLanguageTag());
        loadBundle();
    }

    public ResourceBundle getBundle() {
        return currentBundle;
    }

    public String get(String key) {
        return currentBundle.getString(key);
    }

    public boolean isRTL() {
        return AR.getLanguage().equals(currentLocale.getLanguage());
    }

    private void loadBundle() {
        currentBundle = ResourceBundle.getBundle(BUNDLE_BASE, currentLocale);
    }
}
