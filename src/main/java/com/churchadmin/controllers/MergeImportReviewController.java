package com.churchadmin.controllers;

import com.churchadmin.models.MergeImportReport;
import com.churchadmin.models.PendingMemberRecord;
import com.churchadmin.models.SnapshotPayload;
import com.churchadmin.services.LocaleService;
import com.churchadmin.services.MergeImportService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;

import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Controller for the Merge Import Review screen.
 *
 * Opened after {@link MergeImportService#analyze} completes.
 * Automatic records (new members, new transactions) are NOT applied when the
 * screen opens — the admin must click <em>Apply</em>.
 *
 * All service calls run on background daemon threads; UI updates are delivered
 * via {@code Platform.runLater()} or {@code Task.setOnSucceeded()}.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class MergeImportReviewController implements Initializable {

    // ── Spring beans ──────────────────────────────────────────────────────────

    private final MergeImportService   mergeImportService;
    private final MainLayoutController mainController;
    private final LocaleService        localeService;

    // ── FXML — header ─────────────────────────────────────────────────────────

    @FXML private Label lblSubtitle;

    // ── FXML — summary bar ────────────────────────────────────────────────────

    @FXML private Label lblNewMembersCount;
    @FXML private Label lblNewTransactionsCount;
    @FXML private Label lblSkippedCount;
    @FXML private Label lblPendingCount;
    @FXML private VBox  cardPending;

    // ── FXML — pending table ──────────────────────────────────────────────────

    @FXML private TableView<PendingMemberRecord> tblPendingMembers;

    // ── FXML — action bar ─────────────────────────────────────────────────────

    @FXML private Button btnApply;
    @FXML private Button btnAcceptAll;
    @FXML private Button btnKeepAll;
    @FXML private Button btnDone;

    // ── FXML — status bar ─────────────────────────────────────────────────────

    @FXML private ProgressIndicator progressIndicator;
    @FXML private Label             statusLabel;

    // ── State ─────────────────────────────────────────────────────────────────

    private SnapshotPayload   payload;
    private MergeImportReport report;

    private ObservableList<PendingMemberRecord> pendingItems;
    private boolean newRecordsApplied = false;

    private ResourceBundle bundle;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        this.bundle = rb;
        progressIndicator.setVisible(false);
    }

    /**
     * Called by {@link SnapshotsController} after the FXML is loaded.
     * Automatic records are NOT applied at this point.
     */
    public void initData(SnapshotPayload payload, MergeImportReport report) {
        this.payload = payload;
        this.report  = report;

        // Subtitle — source file info
        String exportedBy = payload.getExportedBy() != null ? payload.getExportedBy() : "?";
        String exportedAt = payload.getExportedAt() != null ? payload.getExportedAt().toString() : "?";
        lblSubtitle.setText(str("merge.review.subtitle") + ": " + exportedBy + " \u2014 " + exportedAt);

        // Summary bar
        lblNewMembersCount.setText(String.valueOf(report.getNewMembers()));
        lblNewTransactionsCount.setText(String.valueOf(report.getNewTransactions()));
        lblSkippedCount.setText(String.valueOf(report.totalSkipped()));
        lblPendingCount.setText(String.valueOf(report.totalPending()));

        // Amber highlight on pending card only when there are pending records
        if (report.totalPending() > 0) {
            if (!cardPending.getStyleClass().contains("summary-card-warn")) {
                cardPending.getStyleClass().add("summary-card-warn");
            }
        } else {
            cardPending.getStyleClass().remove("summary-card-warn");
        }

        // Pending members table
        pendingItems = FXCollections.observableArrayList(report.getPendingMembers());
        buildPendingTable();

        newRecordsApplied = false;
        btnApply.setDisable(false);
        setActionsEnabled(true);
        statusLabel.setText("");
    }

    // ── Action handlers ───────────────────────────────────────────────────────

    @FXML
    private void onApply() {
        setActionsEnabled(false);
        setStatus(str("merge.status.applying"), false);
        progressIndicator.setVisible(true);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                mergeImportService.applyAutomatic(payload, report);
                return null;
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            progressIndicator.setVisible(false);
            setActionsEnabled(true);
            newRecordsApplied = true;
            btnApply.setDisable(true);   // prevent double-apply; set AFTER setActionsEnabled
            setStatus(str("merge.status.applied")
                    + " \u2014 " + report.getNewMembers() + " " + str("merge.summary.new.members")
                    + ", " + report.getNewTransactions() + " " + str("merge.summary.new.transactions"),
                    false);
        }));
        task.setOnFailed(e -> Platform.runLater(() -> {
            progressIndicator.setVisible(false);
            setActionsEnabled(true);
            setStatus(str("common.error") + ": " + task.getException().getMessage(), true);
            log.error("applyAutomatic failed", task.getException());
        }));
        startDaemon(task);
    }

    @FXML
    private void onAcceptAll() {
        List<PendingMemberRecord> remaining = unresolvedPending();
        if (remaining.isEmpty()) {
            setStatus(str("merge.no.pending"), false);
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(str("common.confirm"));
        confirm.setHeaderText(null);
        confirm.setContentText(str("merge.confirm.accept.all"));
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;

            setActionsEnabled(false);
            progressIndicator.setVisible(true);

            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    for (PendingMemberRecord p : remaining) {
                        mergeImportService.applyPendingMember(p);
                    }
                    return null;
                }
            };
            task.setOnSucceeded(e -> Platform.runLater(() -> {
                progressIndicator.setVisible(false);
                pendingItems.clear();
                lblPendingCount.setText("0");
                cardPending.getStyleClass().remove("summary-card-warn");

                setActionsEnabled(true);
                setStatus(str("merge.status.member.accepted"), false);
            }));
            task.setOnFailed(e -> Platform.runLater(() -> {
                progressIndicator.setVisible(false);
                setActionsEnabled(true);
                setStatus(str("common.error") + ": " + task.getException().getMessage(), true);
                log.error("acceptAll failed", task.getException());
            }));
            startDaemon(task);
        });
    }

    @FXML
    private void onKeepAll() {
        List<PendingMemberRecord> remaining = unresolvedPending();
        if (remaining.isEmpty()) {
            setStatus(str("merge.no.pending"), false);
            return;
        }

        setActionsEnabled(false);
        progressIndicator.setVisible(true);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                for (PendingMemberRecord p : remaining) {
                    mergeImportService.discardPendingMember(p);
                }
                return null;
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            progressIndicator.setVisible(false);
            pendingItems.clear();
            lblPendingCount.setText("0");
            cardPending.getStyleClass().remove("summary-card-warn");
            setActionsEnabled(true);
            setStatus(str("merge.status.member.kept"), false);
        }));
        task.setOnFailed(e -> Platform.runLater(() -> {
            progressIndicator.setVisible(false);
            setActionsEnabled(true);
            setStatus(str("common.error") + ": " + task.getException().getMessage(), true);
            log.error("keepAll failed", task.getException());
        }));
        startDaemon(task);
    }

    @FXML
    private void onDone() {
        boolean hasUnappliedNew     = !newRecordsApplied && report.totalNew() > 0;
        boolean hasUnresolvedPending = !unresolvedPending().isEmpty();

        if (hasUnappliedNew || hasUnresolvedPending) {
            StringBuilder msg = new StringBuilder();
            if (hasUnappliedNew) {
                msg.append(java.text.MessageFormat.format(str("merge.done.warning.new"), report.totalNew()))
                   .append("\n");
            }
            if (hasUnresolvedPending) {
                msg.append(java.text.MessageFormat.format(str("merge.done.warning.pending"), unresolvedPending().size()))
                   .append("\n");
            }
            msg.append("\n").append(str("merge.done.warning.suffix"));

            Alert warn = new Alert(Alert.AlertType.CONFIRMATION);
            warn.setTitle(str("common.confirm"));
            warn.setHeaderText(null);
            warn.setContentText(msg.toString());
            warn.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.OK) mainController.navigateTo("snapshots");
            });
        } else {
            mainController.navigateTo("snapshots");
        }
    }

    // ── Per-row actions ───────────────────────────────────────────────────────

    private void onKeepLocalRow(PendingMemberRecord pending) {
        setActionsEnabled(false);
        progressIndicator.setVisible(true);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                mergeImportService.discardPendingMember(pending);
                return null;
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            progressIndicator.setVisible(false);
            pendingItems.remove(pending);
            updatePendingCount();
            setActionsEnabled(true);
            setStatus(str("merge.status.member.kept"), false);
        }));
        task.setOnFailed(e -> Platform.runLater(() -> {
            progressIndicator.setVisible(false);
            setActionsEnabled(true);
            setStatus(str("common.error") + ": " + task.getException().getMessage(), true);
            log.error("discardPendingMember failed", task.getException());
        }));
        startDaemon(task);
    }

    private void onAcceptIncomingRow(PendingMemberRecord pending) {
        setActionsEnabled(false);
        progressIndicator.setVisible(true);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                mergeImportService.applyPendingMember(pending);
                return null;
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            progressIndicator.setVisible(false);
            pendingItems.remove(pending);
            updatePendingCount();
            setActionsEnabled(true);
            setStatus(str("merge.status.member.accepted"), false);
        }));
        task.setOnFailed(e -> Platform.runLater(() -> {
            progressIndicator.setVisible(false);
            setActionsEnabled(true);
            setStatus(str("common.error") + ": " + task.getException().getMessage(), true);
            log.error("applyPendingMember failed", task.getException());
        }));
        startDaemon(task);
    }

    // ── Table building ────────────────────────────────────────────────────────

    /**
     * Dynamically build the pending-members TableView.
     *
     * Always shows a member name column for identification.
     * Then one column per changed field (union across all pending records),
     * each displaying {@code local → incoming} with a gold conflict-cell highlight.
     * Last column: Keep Local / Accept Incoming action buttons.
     */
    private void buildPendingTable() {
        tblPendingMembers.getColumns().clear();
        tblPendingMembers.setItems(pendingItems);
        tblPendingMembers.setPlaceholder(new Label(str("merge.no.pending")));

        if (pendingItems.isEmpty()) return;

        // Member name column — always shown for identification
        TableColumn<PendingMemberRecord, String> nameCol = new TableColumn<>(str("members.field.fullName"));
        nameCol.setCellValueFactory(cell -> {
            Map<String, Object> local = cell.getValue().getLocalRecord();
            Object name = (local != null) ? local.get("full_name") : null;
            return new SimpleStringProperty(name != null ? name.toString() : "\u2014");
        });
        nameCol.setPrefWidth(180);
        tblPendingMembers.getColumns().add(nameCol);

        // Union of all changed fields across all pending records
        LinkedHashSet<String> allFields = new LinkedHashSet<>();
        for (PendingMemberRecord p : pendingItems) {
            if (p.getChangedFields() != null) allFields.addAll(p.getChangedFields());
        }

        // One column per changed field
        for (String field : allFields) {
            TableColumn<PendingMemberRecord, String> col = new TableColumn<>(field);
            col.setCellValueFactory(cell -> {
                PendingMemberRecord p = cell.getValue();
                Object localVal    = p.getLocalRecord()    != null ? p.getLocalRecord().get(field)    : null;
                Object incomingVal = p.getIncomingRecord() != null ? p.getIncomingRecord().get(field) : null;
                return new SimpleStringProperty(
                        formatCellValue(localVal) + " \u2192 " + formatCellValue(incomingVal));
            });
            col.setCellFactory(tc -> new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    getStyleClass().removeAll("conflict-cell");
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        setText(item);
                        getStyleClass().add("conflict-cell");
                    }
                }
            });
            col.setPrefWidth(180);
            tblPendingMembers.getColumns().add(col);
        }

        // Actions column
        TableColumn<PendingMemberRecord, Void> actionsCol = new TableColumn<>(str("snapshots.col.actions"));
        actionsCol.setPrefWidth(260);
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button btnKeep   = new Button(str("merge.button.keep.local"));
            private final Button btnAccept = new Button(str("merge.button.accept.incoming"));
            private final HBox   box       = new HBox(6, btnKeep, btnAccept);

            {
                btnKeep.getStyleClass().add("btn-secondary");
                btnAccept.getStyleClass().add("btn-primary");
                box.setAlignment(Pos.CENTER_LEFT);

                btnKeep.setOnAction(e -> {
                    PendingMemberRecord p = getTableRow().getItem();
                    if (p != null) onKeepLocalRow(p);
                });
                btnAccept.setOnAction(e -> {
                    PendingMemberRecord p = getTableRow().getItem();
                    if (p != null) onAcceptIncomingRow(p);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });
        tblPendingMembers.getColumns().add(actionsCol);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<PendingMemberRecord> unresolvedPending() {
        List<PendingMemberRecord> result = new ArrayList<>();
        for (PendingMemberRecord p : pendingItems) {
            if (!p.isResolved()) result.add(p);
        }
        return result;
    }

    private void updatePendingCount() {
        int remaining = pendingItems.size();
        lblPendingCount.setText(String.valueOf(remaining));
        if (remaining == 0) {
            cardPending.getStyleClass().remove("summary-card-warn");
        }
    }

    private String formatCellValue(Object value) {
        if (value == null) return "\u2014";
        String s = value.toString().trim();
        return s.isEmpty() ? "\u2014" : s;
    }

    private String str(String key) {
        if (bundle == null) bundle = localeService.getBundle();
        try { return bundle.getString(key); } catch (Exception e) { return key; }
    }

    private void setStatus(String message, boolean error) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().removeAll("status-error", "status-ok");
        statusLabel.getStyleClass().add(error ? "status-error" : "status-ok");
    }

    private void setActionsEnabled(boolean enabled) {
        btnApply.setDisable(!enabled);
        btnAcceptAll.setDisable(!enabled);
        btnKeepAll.setDisable(!enabled);
        btnDone.setDisable(!enabled);
    }

    private void startDaemon(Task<?> task) {
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }
}
