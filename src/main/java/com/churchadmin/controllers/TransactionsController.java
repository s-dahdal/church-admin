package com.churchadmin.controllers;

import com.churchadmin.config.JavaFXConfig;
import com.churchadmin.models.Transaction;
import com.churchadmin.models.TransactionCategory;
import com.churchadmin.models.enums.TransactionType;
import com.churchadmin.services.FinancialService;
import com.churchadmin.services.LocaleService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.net.URL;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequiredArgsConstructor
public class TransactionsController implements Initializable {

    // ── Spring beans ──────────────────────────────────────────────────────────
    private final FinancialService               financialService;
    private final MainLayoutController           mainLayoutController;
    private final JavaFXConfig.FXMLLoaderFactory fxmlLoaderFactory;
    private final LocaleService                  localeService;

    // ── FXML — balance strip ──────────────────────────────────────────────────
    @FXML private Label lblBalance;
    @FXML private Label lblIncome;
    @FXML private Label lblExpense;

    // ── FXML — filter bar ─────────────────────────────────────────────────────
    @FXML private DatePicker                   dateFromPicker;
    @FXML private DatePicker                   dateToPicker;
    @FXML private ComboBox<String>             typeFilter;
    @FXML private ComboBox<String>             categoryFilter;
    @FXML private TextField                    searchField;

    // ── FXML — table ──────────────────────────────────────────────────────────
    @FXML private TableView<Transaction>                          transactionsTable;
    @FXML private TableColumn<Transaction, LocalDate>             colDate;
    @FXML private TableColumn<Transaction, String>                colDescription;
    @FXML private TableColumn<Transaction, TransactionCategory>   colCategory;
    @FXML private TableColumn<Transaction, TransactionType> colType;
    @FXML private TableColumn<Transaction, BigDecimal>            colAmount;
    @FXML private TableColumn<Transaction, String> colMember;

    // ── FXML — footer ─────────────────────────────────────────────────────────
    @FXML private Label countLabel;

    // ── State ─────────────────────────────────────────────────────────────────
    private List<Transaction>          allTransactions  = new ArrayList<>();
    private List<TransactionCategory>  allCategories    = new ArrayList<>();
    private ResourceBundle             bundle;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        this.bundle = rb;
        setupTableColumns();
        setupFilters();
        loadData();
        wireListeners();
        transactionsTable.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                Transaction selected = transactionsTable.getSelectionModel().getSelectedItem();
                if (selected != null) openDetail(selected);
            }
        });
    }

    // ── Private setup ─────────────────────────────────────────────────────────

    private void setupTableColumns() {
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));

        colDescription.setCellValueFactory(new PropertyValueFactory<>("description"));

        // Category column — show name or "—"
        colCategory.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(TransactionCategory cat, boolean empty) {
                super.updateItem(cat, empty);
                setText(empty ? null : (cat != null ? cat.getName() : "\u2014"));
            }
        });
        colCategory.setCellValueFactory(new PropertyValueFactory<>("category"));

        // Type column — localised label
        colType.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(TransactionType type, boolean empty) {
                super.updateItem(type, empty);
                if (empty || type == null) { setText(null); setStyle(""); return; }
                if (type == TransactionType.INCOME) {
                    setText(bundle.containsKey("transactions.type.income")
                            ? bundle.getString("transactions.type.income") : "Income");
                    setStyle("-fx-text-fill: #5C7A3E; -fx-font-weight: bold;");
                } else {
                    setText(bundle.containsKey("transactions.type.expense")
                            ? bundle.getString("transactions.type.expense") : "Expense");
                    setStyle("-fx-text-fill: #8B3A2A; -fx-font-weight: bold;");
                }
            }
        });
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));

        // Amount column — right-aligned, colored
        colAmount.setCellFactory(col -> new TableCell<>() {
            private final NumberFormat fmt = NumberFormat.getNumberInstance(localeService.getCurrentLocale());
            {
                setStyle("-fx-alignment: CENTER-RIGHT;");
                fmt.setMinimumFractionDigits(2);
                fmt.setMaximumFractionDigits(2);
            }
            @Override
            protected void updateItem(BigDecimal amount, boolean empty) {
                super.updateItem(amount, empty);
                if (empty || amount == null) { setText(null); setStyle("-fx-alignment: CENTER-RIGHT;"); return; }
                Transaction tx = (Transaction) getTableRow().getItem();
                setText("\u20ac " + fmt.format(amount));
                String color = (tx != null && tx.getType() == TransactionType.INCOME)
                        ? "#5C7A3E" : "#8B3A2A";
                setStyle("-fx-alignment: CENTER-RIGHT; -fx-text-fill: " + color + "; -fx-font-weight: bold;");
            }
        });
        colAmount.setCellValueFactory(new PropertyValueFactory<>("amount"));

        // Member column — show name or "—"
        colMember.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setText(null); return; }
                Transaction tx = (Transaction) getTableRow().getItem();
                setText((tx != null && tx.getMember() != null) ? tx.getMember().getFullName() : "\u2014");
            }
        });
    }

    private void setupFilters() {
        String all = bundle.getString("common.all");
        String income  = bundle.containsKey("transactions.type.income")  ? bundle.getString("transactions.type.income")  : "Income";
        String expense = bundle.containsKey("transactions.type.expense") ? bundle.getString("transactions.type.expense") : "Expense";

        typeFilter.getItems().addAll(all, income, expense);
        typeFilter.getSelectionModel().selectFirst();

        // Category filter populated after data load
    }

    private void loadData() {
        allTransactions = financialService.findAll();
        allCategories = financialService.findAllCategories();
        refreshCategoryFilter();
        applyFilters();
    }

    private void refreshCategoryFilter() {
        String all = bundle.getString("common.all");
        String current = categoryFilter.getValue();
        categoryFilter.getItems().clear();
        categoryFilter.getItems().add(all);
        allCategories.stream()
                .map(TransactionCategory::getName)
                .distinct()
                .sorted()
                .forEach(name -> categoryFilter.getItems().add(name));
        if (current != null && categoryFilter.getItems().contains(current)) {
            categoryFilter.setValue(current);
        } else {
            categoryFilter.getSelectionModel().selectFirst();
        }
    }

    private void wireListeners() {
        searchField.textProperty().addListener((obs, old, val) -> applyFilters());
        typeFilter.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, val) -> applyFilters());
        categoryFilter.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, val) -> applyFilters());
        dateFromPicker.valueProperty().addListener((obs, old, val) -> applyFilters());
        dateToPicker.valueProperty().addListener((obs, old, val) -> applyFilters());
    }

    private void applyFilters() {
        String all     = bundle.getString("common.all");
        String typeSel = typeFilter.getSelectionModel().getSelectedItem();
        String catSel  = categoryFilter.getSelectionModel().getSelectedItem();
        String query   = searchField.getText();
        LocalDate from = dateFromPicker.getValue();
        LocalDate to   = dateToPicker.getValue();

        String incomeLabel = bundle.containsKey("transactions.type.income")
                ? bundle.getString("transactions.type.income") : "Income";

        TransactionType typeFilter = null;
        if (typeSel != null && !typeSel.equals(all)) {
            typeFilter = typeSel.equals(incomeLabel)
                    ? TransactionType.INCOME
                    : TransactionType.EXPENSE;
        }
        final TransactionType typeFilterFinal = typeFilter;

        String catFilterFinal = (catSel != null && !catSel.equals(all)) ? catSel : null;

        List<Transaction> filtered = allTransactions.stream()
                .filter(t -> from == null || !t.getDate().isBefore(from))
                .filter(t -> to == null   || !t.getDate().isAfter(to))
                .filter(t -> typeFilterFinal == null || t.getType() == typeFilterFinal)
                .filter(t -> catFilterFinal == null
                        || (t.getCategory() != null && catFilterFinal.equals(t.getCategory().getName())))
                .filter(t -> {
                    if (query == null || query.isBlank()) return true;
                    String q = query.toLowerCase();
                    return t.getDescription() != null && t.getDescription().toLowerCase().contains(q);
                })
                .collect(Collectors.toList());

        transactionsTable.setItems(FXCollections.observableArrayList(filtered));
        updateBalanceStrip(filtered);
        updateCount(filtered.size());
    }

    private void updateBalanceStrip(List<Transaction> transactions) {
        NumberFormat fmt = NumberFormat.getNumberInstance(localeService.getCurrentLocale());
        fmt.setMinimumFractionDigits(2);
        fmt.setMaximumFractionDigits(2);

        BigDecimal income = transactions.stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal expense = transactions.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal balance = income.subtract(expense);

        lblBalance.setText("\u20ac " + fmt.format(balance));
        lblIncome.setText("\u20ac " + fmt.format(income));
        lblExpense.setText("\u20ac " + fmt.format(expense));
    }

    private void updateCount(int count) {
        if (bundle != null) {
            String pattern = bundle.containsKey("transactions.count")
                    ? bundle.getString("transactions.count")
                    : "Transactions: {0}";
            countLabel.setText(MessageFormat.format(pattern, count));
        }
    }

    private void openDetail(Transaction transaction) {
        try {
            FXMLLoader loader = fxmlLoaderFactory.create();
            URL resource = getClass().getResource("/fxml/TransactionDetail.fxml");
            if (resource == null) {
                log.error("TransactionDetail.fxml not found");
                return;
            }
            loader.setLocation(resource);
            loader.setResources(localeService.getBundle());
            Node view = loader.load();
            TransactionDetailController ctrl = loader.getController();
            ctrl.setCloseAction(() -> mainLayoutController.navigateTo("transactions"));
            ctrl.setTransaction(transaction);
            mainLayoutController.showView(view);
        } catch (Exception e) {
            log.error("Failed to open transaction detail", e);
        }
    }

    // ── FXML handlers ─────────────────────────────────────────────────────────

    @FXML
    private void onAddTransaction() {
        openDetail(null);
    }
}
