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
import java.util.ResourceBundle;

/**
 * Controls the main application shell — sidebar navigation and content swapping.
 *
 * Each nav button's userData maps to a FXML filename.
 * Clicking a button loads /fxml/{screen}.fxml into the center StackPane.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class MainLayoutController implements Initializable {

    @FXML private StackPane contentArea;

    private final JavaFXConfig.FXMLLoaderFactory fxmlLoaderFactory;
    private final LocaleService localeService;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        navigateTo("dashboard");
    }

    @FXML
    public void onNav(ActionEvent event) {
        Button btn = (Button) event.getSource();
        String screen = (String) btn.getUserData();
        navigateTo(screen);
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
        } catch (IOException e) {
            log.error("Failed to load screen [{}]: {}", screen, e.getMessage(), e);
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
