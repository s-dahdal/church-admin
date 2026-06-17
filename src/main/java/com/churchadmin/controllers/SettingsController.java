package com.churchadmin.controllers;

import com.churchadmin.app.MainWindow;
import com.churchadmin.services.LocaleService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;

import java.net.URL;
import java.util.Locale;
import java.util.ResourceBundle;

@Slf4j
@Controller
@RequiredArgsConstructor
public class SettingsController implements Initializable {

    private final LocaleService localeService;
    private final MainWindow    mainWindow;

    @FXML private ComboBox<Locale> languageComboBox;
    @FXML private Button           saveButton;

    private ResourceBundle bundle;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        this.bundle = rb;

        languageComboBox.setItems(
                FXCollections.observableArrayList(LocaleService.SUPPORTED_LOCALES));

        // Display each locale in its own language ("English", "Nederlands", "العربية")
        StringConverter<Locale> converter = new StringConverter<>() {
            @Override
            public String toString(Locale locale) {
                if (locale == null) return "";
                String native_ = locale.getDisplayLanguage(locale);
                return native_.isEmpty() ? locale.toLanguageTag()
                        : Character.toUpperCase(native_.charAt(0)) + native_.substring(1);
            }
            @Override public Locale fromString(String s) { return null; }
        };
        languageComboBox.setConverter(converter);
        languageComboBox.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Locale locale, boolean empty) {
                super.updateItem(locale, empty);
                setText(empty || locale == null ? null : converter.toString(locale));
            }
        });
        languageComboBox.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Locale locale, boolean empty) {
                super.updateItem(locale, empty);
                setText(empty || locale == null ? "" : converter.toString(locale));
            }
        });

        languageComboBox.setValue(localeService.getCurrentLocale());
    }

    // ── FXML handlers ─────────────────────────────────────────────────────────

    @FXML
    private void onSave() {
        Locale selected = languageComboBox.getValue();
        if (selected == null) return;

        if (selected.equals(localeService.getCurrentLocale())) {
            // Nothing changed
            String msg = bundle != null && bundle.containsKey("settings.noChange")
                    ? bundle.getString("settings.noChange")
                    : "The selected language is already active.";
            new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait();
            return;
        }

        localeService.setLocale(selected);

        // Reload the entire main window so every nav button, screen, and
        // RTL/LTR stylesheet reflects the new locale immediately.
        try {
            Stage stage = (Stage) saveButton.getScene().getWindow();
            mainWindow.show(stage);
        } catch (Exception e) {
            log.error("Failed to reload UI after locale change", e);
            new Alert(Alert.AlertType.ERROR,
                    "Language saved. Please restart the application to apply the change.")
                    .showAndWait();
        }
    }
}
