package com.churchadmin.app;

import com.churchadmin.services.SnapshotService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Application entry point.
 *
 * Spring Boot + JavaFX integration pattern:
 *  1. JavaFX launches via Application.launch()
 *  2. init() boots the Spring context
 *  3. start() loads the main window FXML via the Spring-aware FXMLLoader
 *  4. All JavaFX @Controllers are Spring beans with @Autowired support
 *
 * On startup:
 *  - Ensures data directories exist (~/.churchadmin/)
 *  - Triggers the daily auto-snapshot (once per calendar day)
 *  - Opens the main application window
 */
@Slf4j
@SpringBootApplication(scanBasePackages = "com.churchadmin")
@EnableJpaRepositories(basePackages = "com.churchadmin.repositories")
@EntityScan(basePackages = "com.churchadmin.models")
public class ChurchAdminApplication extends Application {

    private ConfigurableApplicationContext springContext;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void init() throws Exception {
        // Ensure ~/.churchadmin/ and subdirs exist before Spring tries to open the DB
        ensureDataDirectories();

        // Boot Spring — all beans wired, Flyway runs migrations, JPA opens SQLite
        springContext = SpringApplication.run(ChurchAdminApplication.class);

        // Daily snapshot on startup
        try {
            SnapshotService snapshotService = springContext.getBean(SnapshotService.class);
            snapshotService.createDailySnapshotIfNeeded();
        } catch (Exception e) {
            log.warn("Daily snapshot failed (non-fatal): {}", e.getMessage());
        }
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        MainWindow mainWindow = springContext.getBean(MainWindow.class);
        mainWindow.show(primaryStage);
    }

    @Override
    public void stop() {
        log.info("Application shutting down");
        springContext.close();
        Platform.exit();
    }

    private void ensureDataDirectories() throws IOException {
        String home = System.getProperty("user.home");
        Files.createDirectories(Path.of(home, ".churchadmin"));
        Files.createDirectories(Path.of(home, ".churchadmin", "snapshots"));
        Files.createDirectories(Path.of(home, ".churchadmin", "logs"));
    }
}
