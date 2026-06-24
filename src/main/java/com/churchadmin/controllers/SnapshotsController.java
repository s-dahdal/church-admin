package com.churchadmin.controllers;

import com.churchadmin.config.JavaFXConfig;
import com.churchadmin.models.MergeImportReport;
import com.churchadmin.models.SnapshotEntry;
import com.churchadmin.models.SnapshotPayload;
import com.churchadmin.models.enums.SnapshotType;
import com.churchadmin.services.LocaleService;
import com.churchadmin.services.MergeImportService;
import com.churchadmin.services.SnapshotService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller for the Snapshots screen.
 *
 * All SnapshotService calls run on daemon background threads.
 * Results and errors are delivered back on the JavaFX Application Thread.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class SnapshotsController implements Initializable {

    // ── Spring beans ──────────────────────────────────────────────────────────

    private final SnapshotService              snapshotService;
    private final MergeImportService           mergeImportService;
    private final MainLayoutController         mainController;
    private final JavaFXConfig.FXMLLoaderFactory fxmlLoaderFactory;
    private final LocaleService                localeService;

    // ── FXML — toolbar ────────────────────────────────────────────────────────

    @FXML private Button btnCreateSnapshot;
    @FXML private Button btnImportExternal;
    @FXML private Button btnMergeImport;

    // ── FXML — table ──────────────────────────────────────────────────────────

    @FXML private TableView<SnapshotEntry>              snapshotsTable;
    @FXML private TableColumn<SnapshotEntry, String>   colDateTime;
    @FXML private TableColumn<SnapshotEntry, String>   colType;
    @FXML private TableColumn<SnapshotEntry, String>   colSize;
    @FXML private TableColumn<SnapshotEntry, Void>     colActions;

    // ── FXML — status bar ─────────────────────────────────────────────────────

    @FXML private Label             statusLabel;
    @FXML private ProgressIndicator progressIndicator;

    // ── State ─────────────────────────────────────────────────────────────────

    private ResourceBundle bundle;
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd  HH:mm:ss");

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        this.bundle = rb;
        setupTableColumns();
        loadSnapshots();
    }

    // ── Table setup ───────────────────────────────────────────────────────────

    private void setupTableColumns() {
        // DateTime column
        colDateTime.setCellValueFactory(cell -> {
            SnapshotEntry e = cell.getValue();
            String text = (e.getCreatedAt() != null) ? e.getCreatedAt().format(DT_FMT) : "";
            return new SimpleStringProperty(text);
        });

        // Type column — i18n label via bundle key
        colType.setCellValueFactory(cell ->
                new SimpleStringProperty(localizeType(cell.getValue())));

        // Size column
        colSize.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getFormattedSize()));

        // Actions column — Export / Restore / Delete buttons per row
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnExport  = new Button(str("snapshots.action.export"));
            private final Button btnRestore = new Button(str("snapshots.action.restore"));
            private final Button btnDelete  = new Button(str("snapshots.action.delete"));
            private final HBox box          = new HBox(6, btnExport, btnRestore, btnDelete);

            {
                btnExport.getStyleClass().add("btn-secondary");
                btnRestore.getStyleClass().add("btn-primary");
                btnDelete.getStyleClass().add("btn-danger");
                box.setAlignment(Pos.CENTER_LEFT);

                btnExport.setOnAction(e -> {
                    SnapshotEntry entry = getTableRow().getItem();
                    if (entry != null) onExport(entry);
                });
                btnRestore.setOnAction(e -> {
                    SnapshotEntry entry = getTableRow().getItem();
                    if (entry != null) onRestore(entry);
                });
                btnDelete.setOnAction(e -> {
                    SnapshotEntry entry = getTableRow().getItem();
                    if (entry != null) onDelete(entry);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private void loadSnapshots() {
        try {
            List<SnapshotEntry> snapshots = snapshotService.listSnapshots();
            snapshotsTable.setItems(FXCollections.observableArrayList(snapshots));
            setStatus(str("snapshots.status.loaded"), false);
        } catch (Exception e) {
            log.error("Failed to list snapshots", e);
            setStatus(str("common.error") + ": " + e.getMessage(), true);
        }
    }

    // ── Toolbar actions ───────────────────────────────────────────────────────

    @FXML
    private void onCreateSnapshot() {
        String adminName = System.getProperty("user.name", "admin");
        setStatus(str("snapshots.status.creating"), false);
        setToolbarEnabled(false);
        progressIndicator.setVisible(true);

        Task<Path> task = new Task<>() {
            @Override
            protected Path call() throws Exception {
                return snapshotService.createSnapshot(SnapshotType.MANUAL, adminName);
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            setStatus(str("snapshots.status.created"), false);
            progressIndicator.setVisible(false);
            setToolbarEnabled(true);
            loadSnapshots();
        }));
        task.setOnFailed(e -> Platform.runLater(() -> {
            setStatus(str("snapshots.error.import_failed") + ": " + task.getException().getMessage(), true);
            progressIndicator.setVisible(false);
            setToolbarEnabled(true);
        }));
        startDaemon(task);
    }

    @FXML
    private void onMergeImport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(str("snapshots.button.merge.import"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON files (*.json)", "*.json"));
        File chosen = chooser.showOpenDialog(btnMergeImport.getScene().getWindow());
        if (chosen == null) return;

        setStatus(str("snapshots.status.importing"), false);
        setToolbarEnabled(false);
        progressIndicator.setVisible(true);

        Task<Object[]> task = new Task<>() {
            @Override
            protected Object[] call() throws Exception {
                SnapshotPayload payload = snapshotService.parseSnapshotPayload(chosen.toPath());
                MergeImportReport report = mergeImportService.analyze(payload);
                return new Object[]{payload, report};
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            progressIndicator.setVisible(false);
            setToolbarEnabled(true);

            Object[] result = task.getValue();
            SnapshotPayload  payload = (SnapshotPayload)  result[0];
            MergeImportReport report = (MergeImportReport) result[1];

            try {
                FXMLLoader loader = fxmlLoaderFactory.create();
                loader.setLocation(getClass().getResource("/fxml/MergeImportReview.fxml"));
                loader.setResources(localeService.getBundle());
                Node view = loader.load();
                MergeImportReviewController controller = loader.getController();
                controller.initData(payload, report);
                mainController.showView(view);
            } catch (Exception ex) {
                log.error("Failed to open MergeImportReview", ex);
                setStatus(str("common.error") + ": " + ex.getMessage(), true);
                showError(str("common.error"), ex.getMessage());
            }
        }));
        task.setOnFailed(e -> Platform.runLater(() -> {
            String msg = task.getException().getMessage();
            setStatus(str("snapshots.error.import_failed") + ": " + msg, true);
            progressIndicator.setVisible(false);
            setToolbarEnabled(true);
            showError(str("common.error"), str("snapshots.error.import_failed") + "\n" + msg);
        }));
        startDaemon(task);
    }

    @FXML
    private void onImportExternal() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(str("snapshots.btn.import"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON files (*.json)", "*.json"));
        File chosen = chooser.showOpenDialog(btnImportExternal.getScene().getWindow());
        if (chosen == null) return;

        setStatus(str("snapshots.status.importing"), false);
        setToolbarEnabled(false);
        progressIndicator.setVisible(true);

        Task<SnapshotEntry> task = new Task<>() {
            @Override
            protected SnapshotEntry call() throws Exception {
                return snapshotService.importExternalSnapshot(chosen.toPath());
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            setStatus(str("snapshots.status.imported"), false);
            progressIndicator.setVisible(false);
            setToolbarEnabled(true);
            loadSnapshots();
        }));
        task.setOnFailed(e -> Platform.runLater(() -> {
            String msg = task.getException().getMessage();
            setStatus(str("snapshots.error.import_failed") + ": " + msg, true);
            progressIndicator.setVisible(false);
            setToolbarEnabled(true);
            showError(str("common.error"), str("snapshots.error.import_failed") + "\n" + msg);
        }));
        startDaemon(task);
    }

    // ── Row actions ───────────────────────────────────────────────────────────

    private void onExport(SnapshotEntry entry) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(str("snapshots.action.export"));
        chooser.setInitialFileName(entry.getFilename());
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON files (*.json)", "*.json"));
        File dest = chooser.showSaveDialog(snapshotsTable.getScene().getWindow());
        if (dest == null) return;

        setStatus(str("snapshots.status.exporting"), false);
        progressIndicator.setVisible(true);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                snapshotService.exportSnapshot(entry.getFilename(), dest.toPath());
                return null;
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            setStatus(str("snapshots.status.exported"), false);
            progressIndicator.setVisible(false);
        }));
        task.setOnFailed(e -> Platform.runLater(() -> {
            setStatus(str("common.error") + ": " + task.getException().getMessage(), true);
            progressIndicator.setVisible(false);
        }));
        startDaemon(task);
    }

    private void onRestore(SnapshotEntry entry) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(str("snapshots.confirm.restore.title"));
        confirm.setHeaderText(null);
        confirm.setContentText(str("snapshots.confirm.restore.body"));
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;

            String adminName = System.getProperty("user.name", "admin");
            setStatus(str("snapshots.status.restoring"), false);
            setToolbarEnabled(false);
            progressIndicator.setVisible(true);

            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    snapshotService.restoreSnapshot(entry.getFilename(), adminName);
                    return null;
                }
            };
            task.setOnSucceeded(e -> Platform.runLater(() -> {
                setStatus(str("snapshots.status.restored"), false);
                progressIndicator.setVisible(false);
                setToolbarEnabled(true);
                loadSnapshots();
                Alert info = new Alert(Alert.AlertType.INFORMATION);
                info.setTitle(str("snapshots.info.restore_complete"));
                info.setHeaderText(null);
                info.setContentText(str("snapshots.info.restore_complete.body"));
                info.showAndWait();
            }));
            task.setOnFailed(e -> Platform.runLater(() -> {
                String msg = task.getException().getMessage();
                setStatus(str("snapshots.error.restore_failed") + ": " + msg, true);
                progressIndicator.setVisible(false);
                setToolbarEnabled(true);
                showError(str("common.error"), str("snapshots.error.restore_failed") + "\n" + msg);
            }));
            startDaemon(task);
        });
    }

    private void onDelete(SnapshotEntry entry) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(str("snapshots.confirm.delete.title"));
        confirm.setHeaderText(null);
        confirm.setContentText(str("snapshots.confirm.delete.body"));
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;

            setStatus(str("snapshots.status.deleting"), false);
            progressIndicator.setVisible(true);

            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    snapshotService.deleteSnapshot(entry.getFilename());
                    return null;
                }
            };
            task.setOnSucceeded(e -> Platform.runLater(() -> {
                setStatus(str("snapshots.status.deleted"), false);
                progressIndicator.setVisible(false);
                loadSnapshots();
            }));
            task.setOnFailed(e -> Platform.runLater(() -> {
                setStatus(str("common.error") + ": " + task.getException().getMessage(), true);
                progressIndicator.setVisible(false);
            }));
            startDaemon(task);
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Look up a ResourceBundle key; fall back to the key itself. */
    private String str(String key) {
        if (bundle == null) return key;
        try { return bundle.getString(key); } catch (Exception e) { return key; }
    }

    /** Localise a SnapshotType using bundle keys, falling back to getTypeLabel(). */
    private String localizeType(SnapshotEntry entry) {
        if (entry.getType() == null) return entry.getTypeLabel();
        String key = switch (entry.getType()) {
            case AUTO_DAILY -> "snapshots.type.auto_daily";
            case PRE_IMPORT -> "snapshots.type.pre_import";
            case MANUAL     -> "snapshots.type.manual";
        };
        return (bundle != null && bundle.containsKey(key))
                ? bundle.getString(key)
                : entry.getTypeLabel();
    }

    private void setStatus(String message, boolean error) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().removeAll("status-error", "status-ok");
        statusLabel.getStyleClass().add(error ? "status-error" : "status-ok");
    }

    private void setToolbarEnabled(boolean enabled) {
        btnCreateSnapshot.setDisable(!enabled);
        btnImportExternal.setDisable(!enabled);
        btnMergeImport.setDisable(!enabled);
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void startDaemon(Task<?> task) {
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }
}