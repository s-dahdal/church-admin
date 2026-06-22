package com.churchadmin.app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import javax.imageio.ImageIO;
import java.awt.Taskbar;
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

        // Boot Spring — all beans wired, Flyway runs migrations, JPA opens SQLite.
        // StartupSnapshotJob (@PostConstruct) handles the daily auto-snapshot.
        springContext = SpringApplication.run(ChurchAdminApplication.class);
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        // Set the macOS Dock icon / Windows taskbar icon via AWT Taskbar API.
        // AWT Toolkit initialisation (triggered by Taskbar.getTaskbar()) nulls out
        // the JavaFX Application Thread's context classloader as a side-effect, which
        // breaks every subsequent FXMLLoader call.  Capture and restore it around the
        // AWT call to avoid the NullPointerException in FXMLLoader.getClassLoader().
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (Taskbar.isTaskbarSupported()) {
            Taskbar tb = Taskbar.getTaskbar();
            if (tb.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                tb.setIconImage(ImageIO.read(
                        getClass().getResourceAsStream("/images/logo.png")));
            }
        }
        Thread.currentThread().setContextClassLoader(cl);
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
