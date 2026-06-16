package com.churchadmin.controllers;

import com.churchadmin.config.JavaFXConfig;
import com.churchadmin.services.LocaleService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controls the main application shell — sidebar navigation and content swapping.
 *
 * Each nav button's userData maps to a FXML filename.
 * Clicking a button loads /fxml/{screen}.fxml into the center StackPane.
 *
 * Active-nav tracking: the button whose userData matches the current top-level
 * screen receives the "active" CSS class.  showView() (used for sub-screens like
 * MemberDetail) does NOT change the active button so the parent section stays
 * highlighted.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class MainLayoutController implements Initializable {

    @FXML private StackPane contentArea;

    // Nav buttons — injected so we can manage the "active" CSS class
    @FXML private Button navDashboard;
    @FXML private Button navMembers;
    @FXML private Button navTransactions;
    @FXML private Button navImportExport;
    @FXML private Button navSnapshots;
    @FXML private Button navReports;
    @FXML private Button navSettings;

    private List<Button> navButtons;

    private final JavaFXConfig.FXMLLoaderFactory fxmlLoaderFactory;
    private final LocaleService localeService;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        navButtons = List.of(
                navDashboard, navMembers, navTransactions,
                navImportExport, navSnapshots, navReports, navSettings);
        navigateTo("dashboard");
    }

    @FXML
    public void onNav(ActionEvent event) {
        Button btn = (Button) event.getSource();
        String screen = (String) btn.getUserData();
        navigateTo(screen);
    }

    /**
     * Show an already-constructed node (e.g. MemberDetail) without changing the
     * active nav button.  Focus is moved to the content area so no nav button
     * retains the :focused highlight.
     */
    public void showView(Node node) {
        contentArea.getChildren().setAll(node);
        contentArea.requestFocus();
    }

    public void navigateTo(String screen) {
        String fxmlPath = "/fxml/" + capitalize(screen) + ".fxml";
        try {
            FXMLLoader loader = fxmlLoaderFactory.create();
            URL resource = getClass().getResource(fxmlPath);
            if (resource == null) {
                log.warn("FXML not found: {}", fxmlPath);
                return;
            }
            loader.setLocation(resource);
            loader.setResources(localeService.getBundle());
            Node view = loader.load();
            contentArea.getChildren().setAll(view);
            setActiveNav(screen);
            contentArea.requestFocus();
        } catch (IOException e) {
            log.error("Failed to load screen [{}]: {}", screen, e.getMessage(), e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setActiveNav(String screen) {
        if (navButtons == null) return;
        for (Button b : navButtons) {
            b.getStyleClass().remove("active");
            if (screen.equals(b.getUserData())) {
                b.getStyleClass().add("active");
            }
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
