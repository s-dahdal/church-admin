package com.churchadmin.app;

import com.churchadmin.config.JavaFXConfig;
import com.churchadmin.services.LocaleService;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Objects;

/**
 * Responsible for loading and displaying the main application window.
 *
 * Uses the Spring-aware FXMLLoaderFactory so all controllers in MainLayout.fxml
 * and any child FXML files are resolved as Spring beans with full @Autowired support.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MainWindow {

    private final JavaFXConfig.FXMLLoaderFactory fxmlLoaderFactory;
    private final LocaleService localeService;

    public void show(Stage stage) throws IOException {
        FXMLLoader loader = fxmlLoaderFactory.create();
        loader.setLocation(getClass().getResource("/fxml/MainLayout.fxml"));
        loader.setResources(localeService.getBundle());

        Parent root = loader.load();

        // Apply RTL orientation for Arabic
        if (localeService.isRTL()) {
            root.setNodeOrientation(javafx.geometry.NodeOrientation.RIGHT_TO_LEFT);
        }

        Scene scene = new Scene(root, 1280, 800);
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/css/app.css")).toExternalForm()
        );
        if (localeService.isRTL()) {
            scene.getStylesheets().add(
                    Objects.requireNonNull(getClass().getResource("/css/rtl.css")).toExternalForm()
            );
        }

        stage.setTitle(localeService.get("app.title"));
        stage.setScene(scene);
        stage.setMinWidth(1024);
        stage.setMinHeight(700);
        stage.show();

        log.info("Main window displayed [locale={}]", localeService.getCurrentLocale());
    }
}
