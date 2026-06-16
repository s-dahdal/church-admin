package com.churchadmin.controllers;

import com.churchadmin.models.Transaction;
import com.churchadmin.services.FinancialService;
import com.churchadmin.services.LocaleService;
import com.churchadmin.services.MemberService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.net.URL;
import java.text.NumberFormat;
import java.util.List;
import java.util.ResourceBundle;

@Controller
@RequiredArgsConstructor
public class DashboardController implements Initializable {

    @FXML private Label lblBalance;
    @FXML private Label lblActiveMembers;
    @FXML private TableView<Transaction> recentTable;
    @FXML private TableColumn<Transaction, String> colDate;
    @FXML private TableColumn<Transaction, String> colDesc;
    @FXML private TableColumn<Transaction, String> colType;
    @FXML private TableColumn<Transaction, BigDecimal> colAmount;

    private final FinancialService financialService;
    private final MemberService memberService;
    private final LocaleService localeService;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupTable();
        refresh();
    }

    private void setupTable() {
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colDesc.setCellValueFactory(new PropertyValueFactory<>("description"));
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colAmount.setCellValueFactory(new PropertyValueFactory<>("amount"));
    }

    public void refresh() {
        BigDecimal balance = financialService.getCurrentBalance();
        NumberFormat fmt = NumberFormat.getCurrencyInstance(localeService.getCurrentLocale());
        lblBalance.setText(fmt.format(balance));

        long activeCount = memberService.countActive();
        lblActiveMembers.setText(String.valueOf(activeCount));

        List<Transaction> recent = financialService.findRecent();
        recentTable.setItems(FXCollections.observableArrayList(recent));
    }
}
