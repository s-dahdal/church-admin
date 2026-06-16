package com.churchadmin.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;

@Configuration
public class I18nConfig {

    /**
     * Loads messages_en.properties, messages_nl.properties, messages_ar.properties
     * from src/main/resources/i18n/.
     *
     * English is the fallback when a key is missing in another locale.
     */
    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        source.setDefaultLocale(java.util.Locale.ENGLISH);
        return source;
    }
}
