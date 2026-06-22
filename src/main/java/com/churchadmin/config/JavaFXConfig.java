package com.churchadmin.config;

import javafx.fxml.FXMLLoader;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bridges Spring's ApplicationContext with JavaFX's FXMLLoader.
 *
 * Key pattern from the ADR:
 *   loader.setControllerFactory(springContext::getBean)
 *
 * This makes every JavaFX @Controller a Spring bean, enabling full
 * @Autowired dependency injection inside controllers.
 */
@Configuration
public class JavaFXConfig {

    /**
     * Factory method that creates FXMLLoaders pre-wired to the Spring context.
     * Controllers defined in FXML files are resolved as Spring beans.
     *
     * Usage in controllers:
     *   FXMLLoader loader = fxmlLoaderFactory.create();
     *   loader.setLocation(getClass().getResource("/fxml/SomeScreen.fxml"));
     *   Parent root = loader.load();
     */
    @Bean
    public FXMLLoaderFactory fxmlLoaderFactory(ApplicationContext applicationContext) {
        return () -> {
            FXMLLoader loader = new FXMLLoader();
            loader.setControllerFactory(applicationContext::getBean);
            // Explicitly pin the classloader so FXMLLoader never falls back to
            // Thread.currentThread().getContextClassLoader(), which AWT Toolkit
            // initialisation can null out on the JavaFX Application Thread.
            loader.setClassLoader(applicationContext.getClassLoader());
            return loader;
        };
    }

    @FunctionalInterface
    public interface FXMLLoaderFactory {
        FXMLLoader create();
    }
}
