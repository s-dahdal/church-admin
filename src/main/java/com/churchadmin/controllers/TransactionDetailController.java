package com.churchadmin.controllers;

import com.churchadmin.config.JavaFXConfig;
import com.churchadmin.models.Member;
import com.churchadmin.models.Transaction;
import com.churchadmin.models.TransactionCategory;
import com.churchadmin.services.FinancialService;
import com.churchadmin.services.LocaleService;
import com.churchadmin.services.MemberService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequiredArgsConstructor
public class TransactionDetailController implements Initializable {

    // ── Spring beans ──────────────────────────────────────────────────────────
    private final FinancialService            financialService;
    private final MemberService               memberService;
    private final MainLayoutController        mainLayoutController;
    private final JavaFXConfig.FXMLLoaderFactory fxmlLoaderFactory;
    private final LocaleService               localeService;

    // ── FXML ──────────────────────────────────────────────────────────────────
    @FXML private Label                         pageTitleLabel;
    @FXML private DatePicker                    datePicker;
    @FXML private ToggleButton                  incomeToggle;
    @FXML private ToggleButton                  expenseToggle;
    @FXML private ComboBox<TransactionCategory> categoryComboBox;
    @FXML private TextField                     amountField;
    @FXML private TextArea                      descriptionArea;
    @FXML private CheckBox                      linkMemberCheckBox;
    @FXML private ComboBox<Member>              memberComboBox;
    @FXML private Button                        saveButton;
    @FXML private Button                        deleteButton;

    // ── State ─────────────────────────────────────────────────────────────────
    private Transaction            currentTransaction;
    private Member                 prefilledMember;
    private boolean                memberLocked;
    private Runnable               closeAction;
    private ResourceBundle         bundle;
    private ToggleGroup            typeToggleGroup;
    private List<Member>           allMembers;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        this.bundle = rb;

        setupTypeToggle();
        setupAmountFormatter();
        setupMemberComboBox();

        datePicker.setValue(LocalDate.now());
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Call after load() to populate an existing transaction for editing. */
    public void setTransaction(Transaction transaction) {
        this.currentTransaction = transaction;
        if (transaction != null) {
            populateForm(transaction);
            deleteButton.setVisible(true);
            deleteButton.setManaged(true);
            pageTitleLabel.setText(bundle != null ? bundle.getString("transactions.edit") : "Edit Transaction");
        } else {
            clearForm();
            deleteButton.setVisible(false);
            deleteButton.setManaged(false);
            pageTitleLabel.setText(bundle != null ? bundle.getString("transactions.add") : "Add Transaction");
        }
        applyMemberLock();
    }

    /**
     * Pre-fill and optionally lock the member field.
     * Call before {@link #setTransaction(Transaction)}.
     */
    public void setPrefilledMember(Member member) {
        this.prefilledMember = member;
    }

    public void setMemberLocked(boolean locked) {
        this.memberLocked = locked;
    }

    /**
     * Callback run on close (save / cancel / delete).
     * If not set, navigates to the Transactions list.
     */
    public void setCloseAction(Runnable action) {
        this.closeAction = action;
    }

    // ── Private setup ─────────────────────────────────────────────────────────

    private void setupTypeToggle() {
        typeToggleGroup = new ToggleGroup();
        incomeToggle.setToggleGroup(typeToggleGroup);
        expenseToggle.setToggleGroup(typeToggleGroup);

        // Income selected by default
        incomeToggle.setSelected(true);
        refreshCategories(Transaction.TransactionType.INCOME);

        typeToggleGroup.selectedToggleProperty().addListener((obs, old, selected) -> {
            if (selected == null) {
                // Prevent deselecting both
                (old != null ? old : incomeToggle).setSelected(true);
                return;
            }
            refreshCategories(getSelectedType());
        });
    }

    private void setupAmountFormatter() {
        UnaryOperator<TextFormatter.Change> filter = change -> {
            String text = change.getControlNewText();
            // Allow digits with at most one decimal separator (. or ,) and up to 2 decimal digits
            if (text.matches("\\d*[.,]?\\d{0,2}")) return change;
            return null;
        };
        amountField.setTextFormatter(new TextFormatter<>(filter));
    }

    private void setupMemberComboBox() {
        allMembers = memberService.findAll();

        memberComboBox.setItems(FXCollections.observableArrayList(allMembers));

        StringConverter<Member> converter = new StringConverter<>() {
            @Override public String toString(Member m) { return m == null ? "" : m.getFullName(); }
            @Override public Member fromString(String s) {
                if (s == null || s.isBlank()) return null;
                return allMembers.stream()
                        .filter(m -> m.getFullName().equalsIgnoreCase(s))
                        .findFirst().orElse(null);
            }
        };
        memberComboBox.setConverter(converter);

        memberComboBox.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Member m, boolean empty) {
                super.updateItem(m, empty);
                setText(empty || m == null ? null : m.getFullName());
            }
        });
        memberComboBox.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Member m, boolean empty) {
                super.updateItem(m, empty);
                setText(empty || m == null ? "" : m.getFullName());
            }
        });

        // Autocomplete filter
        memberComboBox.setEditable(true);
        memberComboBox.getEditor().textProperty().addListener((obs, old, val) -> {
            if (val == null || val.isBlank()) {
                memberComboBox.setItems(FXCollections.observableArrayList(allMembers));
                return;
            }
            String q = val.toLowerCase();
            List<Member> filtered = allMembers.stream()
                    .filter(m -> m.getFullName().toLowerCase().contains(q))
                    .collect(Collectors.toList());
            memberComboBox.setItems(FXCollections.observableArrayList(filtered));
            if (!memberComboBox.isShowing() && !filtered.isEmpty()) {
                memberComboBox.show();
            }
        });
    }

    private void refreshCategories(Transaction.TransactionType type) {
        TransactionCategory previous = categoryComboBox.getValue();
        List<TransactionCategory> cats = financialService.findCategoriesByType(type);
        categoryComboBox.setItems(FXCollections.observableArrayList(cats));
        categoryComboBox.setConverter(new StringConverter<>() {
            @Override public String toString(TransactionCategory c) { return c == null ? "" : c.getName(); }
            @Override public TransactionCategory fromString(String s) { return null; }
        });
        // Re-select same category if it's still valid
        if (previous != null && cats.contains(previous)) {
            categoryComboBox.setValue(previous);
        } else if (!cats.isEmpty()) {
            categoryComboBox.getSelectionModel().selectFirst();
        } else {
            categoryComboBox.setValue(null);
        }
    }

    private void populateForm(Transaction t) {
        datePicker.setValue(t.getDate() != null ? t.getDate() : LocalDate.now());

        if (t.getType() == Transaction.TransactionType.EXPENSE) {
            expenseToggle.setSelected(true);
            refreshCategories(Transaction.TransactionType.EXPENSE);
        } else {
            incomeToggle.setSelected(true);
            refreshCategories(Transaction.TransactionType.INCOME);
        }

        if (t.getCategory() != null) {
            categoryComboBox.setValue(t.getCategory());
        }

        if (t.getAmount() != null) {
            amountField.setText(t.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString());
        }
        descriptionArea.setText(t.getDescription() != null ? t.getDescription() : "");

        if (t.getMember() != null) {
            linkMemberCheckBox.setSelected(true);
            memberComboBox.setValue(t.getMember());
            memberComboBox.setVisible(true);
            memberComboBox.setManaged(true);
        } else {
            linkMemberCheckBox.setSelected(false);
            memberComboBox.setVisible(false);
            memberComboBox.setManaged(false);
        }
    }

    private void clearForm() {
        datePicker.setValue(LocalDate.now());
        incomeToggle.setSelected(true);
        refreshCategories(Transaction.TransactionType.INCOME);
        amountField.setText("");
        descriptionArea.setText("");
        linkMemberCheckBox.setSelected(false);
        memberComboBox.setValue(null);
        memberComboBox.setVisible(false);
        memberComboBox.setManaged(false);
    }

    private void applyMemberLock() {
        if (prefilledMember != null) {
            linkMemberCheckBox.setSelected(true);
            memberComboBox.setValue(prefilledMember);
            memberComboBox.setVisible(true);
            memberComboBox.setManaged(true);
        }
        if (memberLocked) {
            linkMemberCheckBox.setDisable(true);
            memberComboBox.setDisable(true);
            // Default to INCOME when opened from member tab
            if (currentTransaction == null) {
                incomeToggle.setSelected(true);
                refreshCategories(Transaction.TransactionType.INCOME);
            }
        }
    }

    private Transaction.TransactionType getSelectedType() {
        return expenseToggle.isSelected()
                ? Transaction.TransactionType.EXPENSE
                : Transaction.TransactionType.INCOME;
    }

    private BigDecimal parseAmount(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return new BigDecimal(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void close() {
        if (closeAction != null) {
            closeAction.run();
        } else {
            mainLayoutController.navigateTo("transactions");
        }
    }

    // ── FXML handlers ─────────────────────────────────────────────────────────

    @FXML
    private void onLinkMemberToggle() {
        boolean linked = linkMemberCheckBox.isSelected();
        memberComboBox.setVisible(linked);
        memberComboBox.setManaged(linked);
        if (!linked) {
            memberComboBox.setValue(null);
        }
    }

    @FXML
    private void onSave() {
        // Validation
        LocalDate date = datePicker.getValue();
        if (date == null) {
            showWarning(bundle != null ? bundle.getString("transaction.date") : "Date", " is required.");
            return;
        }

        BigDecimal amount = parseAmount(amountField.getText());
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            showWarning(bundle != null ? bundle.getString("validation.amountInvalid")
                    : "Please enter a valid amount greater than zero.");
            return;
        }

        TransactionCategory category = categoryComboBox.getValue();
        if (category == null) {
            showWarning(bundle != null ? bundle.getString("validation.categoryRequired")
                    : "Please select a category.");
            return;
        }

        // Double-submit guard
        saveButton.setDisable(true);
        try {
            Transaction tx = currentTransaction != null ? currentTransaction : new Transaction();
            tx.setDate(date);
            tx.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
            tx.setType(getSelectedType());
            tx.setCategory(category);
            String desc = descriptionArea.getText();
            tx.setDescription(desc != null ? desc.trim() : null);

            if (linkMemberCheckBox.isSelected()) {
                tx.setMember(memberComboBox.getValue());
            } else {
                tx.setMember(null);
            }

            financialService.save(tx);
            close();
        } catch (Exception e) {
            log.error("Failed to save transaction", e);
            saveButton.setDisable(false);
            new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait();
        }
    }

    @FXML
    private void onDelete() {
        if (currentTransaction == null) return;

        String msg = bundle != null
                ? bundle.getString("transactions.confirm.delete")
                : "Are you sure you want to delete this transaction?";

        new Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.OK, ButtonType.CANCEL)
                .showAndWait()
                .ifPresent(btn -> {
                    if (btn == ButtonType.OK) {
                        try {
                            financialService.delete(currentTransaction.getId());
                            close();
                        } catch (Exception e) {
                            log.error("Failed to delete transaction", e);
                            new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait();
                        }
                    }
                });
    }

    @FXML
    private void onCancel() {
        close();
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private void showWarning(String msg) {
        new Alert(Alert.AlertType.WARNING, msg).showAndWait();
    }

    private void showWarning(String field, String suffix) {
        showWarning(field + suffix);
    }
}