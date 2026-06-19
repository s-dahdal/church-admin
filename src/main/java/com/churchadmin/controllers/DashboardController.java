package com.churchadmin.controllers;

import com.churchadmin.models.Transaction;
import com.churchadmin.models.enums.TransactionType;
import com.churchadmin.services.FinancialService;
import com.churchadmin.services.LocaleService;
import com.churchadmin.services.MemberService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.text.NumberFormat;
import java.util.List;
import java.util.ResourceBundle;

@Controller
@RequiredArgsConstructor
public class DashboardController implements Initializable {

    @FXML private Label lblBalance;
    @FXML private Label lblIncome;
    @FXML private Label lblExpense;
    @FXML private Label lblActiveMembers;
    @FXML private TableView<Transaction> recentTable;
    @FXML private TableColumn<Transaction, String>     colDate;
    @FXML private TableColumn<Transaction, String>     colDesc;
    @FXML private TableColumn<Transaction, TransactionType> colType;
    @FXML private TableColumn<Transaction, BigDecimal> colAmount;

    private final FinancialService financialService;
    private final MemberService    memberService;
    private final LocaleService    localeService;
    private final MainLayoutController mainLayoutController;

    private ResourceBundle bundle;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        this.bundle = rb;
        setupTable();
        refresh();
    }

    private void setupTable() {
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colDesc.setCellValueFactory(new PropertyValueFactory<>("description"));

        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colType.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(TransactionType type, boolean empty) {
                super.updateItem(type, empty);
                if (empty || type == null) { setText(null); setStyle(""); return; }
                if (type == TransactionType.INCOME) {
                    setText(bundle != null && bundle.containsKey("transactions.type.income")
                            ? bundle.getString("transactions.type.income") : "Income");
                    setStyle("-fx-text-fill: #5C7A3E;");
                } else {
                    setText(bundle != null && bundle.containsKey("transactions.type.expense")
                            ? bundle.getString("transactions.type.expense") : "Expense");
                    setStyle("-fx-text-fill: #8B3A2A;");
                }
            }
        });

        NumberFormat fmt = NumberFormat.getNumberInstance(localeService.getCurrentLocale());
        fmt.setMinimumFractionDigits(2);
        fmt.setMaximumFractionDigits(2);

        colAmount.setCellValueFactory(new PropertyValueFactory<>("amount"));
        colAmount.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal amount, boolean empty) {
                super.updateItem(amount, empty);
                if (empty || amount == null) { setText(null); setStyle("-fx-alignment: CENTER-RIGHT;"); return; }
                Transaction tx = (Transaction) getTableRow().getItem();
                setText("\u20ac " + fmt.format(amount.setScale(2, RoundingMode.HALF_UP)));
                String color = (tx != null && tx.getType() == TransactionType.INCOME)
                        ? "#5C7A3E" : "#8B3A2A";
                setStyle("-fx-alignment: CENTER-RIGHT; -fx-text-fill: " + color + ";");
            }
        });
    }

    public void refresh() {
        NumberFormat fmt = NumberFormat.getNumberInstance(localeService.getCurrentLocale());
        fmt.setMinimumFractionDigits(2);
        fmt.setMaximumFractionDigits(2);

        BigDecimal balance = financialService.getCurrentBalance();
        lblBalance.setText("\u20ac " + fmt.format(balance.setScale(2, RoundingMode.HALF_UP)));

        BigDecimal income  = financialService.getTotalIncomeAll();
        BigDecimal expense = financialService.getTotalExpenseAll();
        lblIncome.setText("\u20ac " + fmt.format(income.setScale(2, RoundingMode.HALF_UP)));
        lblExpense.setText("\u20ac " + fmt.format(expense.setScale(2, RoundingMode.HALF_UP)));

        long activeCount = memberService.countActive();
        lblActiveMembers.setText(String.valueOf(activeCount));

        List<Transaction> recent = financialService.findRecent();
        recentTable.setItems(FXCollections.observableArrayList(recent));
    }

    @FXML
    private void onViewAllTransactions() {
        mainLayoutController.navigateTo("transactions");
    }
}
