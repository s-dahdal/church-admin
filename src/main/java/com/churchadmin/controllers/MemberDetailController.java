package com.churchadmin.controllers;

import com.churchadmin.models.Member;
import com.churchadmin.models.Transaction;
import com.churchadmin.models.enums.MemberStatus;
import com.churchadmin.models.enums.PaymentMethod;
import com.churchadmin.services.MemberService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.ResourceBundle;

@Slf4j
@Controller
@RequiredArgsConstructor
public class MemberDetailController implements Initializable {

    // ── Spring beans ──────────────────────────────────────────────────────────
    private final MemberService        memberService;
    private final MainLayoutController mainLayoutController;

    // ── FXML — tabs ───────────────────────────────────────────────────────────
    @FXML private TabPane tabPane;

    // ── FXML — Info tab ───────────────────────────────────────────────────────
    @FXML private TextField  memberNumberField;
    @FXML private TextField  fullNameField;
    @FXML private TextField  phoneNumberField;
    @FXML private TextField  emailField;
    @FXML private TextField  addressField;
    @FXML private TextField  postalCodeField;
    @FXML private TextField  cityField;
    @FXML private TextField  placeField;
    @FXML private TextField  parishField;
    @FXML private TextField  partnerNameField;
    @FXML private TextField  partnerPhoneField;
    @FXML private TextField  partnerEmailField;
    @FXML private TextField  child1Field;
    @FXML private TextField  child2Field;
    @FXML private TextField  child3Field;
    @FXML private TextField  child4Field;
    @FXML private TextField  child5Field;
    @FXML private TextField  applicationHolderField;
    @FXML private DatePicker signingDatePicker;
    @FXML private TextField  ibanField;
    @FXML private ComboBox<PaymentMethod> paymentMethodComboBox;
    @FXML private ComboBox<MemberStatus>  statusComboBox;
    @FXML private TextField  categoryField;
    @FXML private TextField  householdGroupField;

    // ── FXML — Transactions tab ───────────────────────────────────────────────
    @FXML private Label                       txMemberNameLabel;
    @FXML private Label                       txBalanceLabel;
    @FXML private TableView<Transaction>      transactionsTable;
    @FXML private TableColumn<Transaction, LocalDate>     colTxDate;
    @FXML private TableColumn<Transaction, BigDecimal>    colTxAmount;
    @FXML private TableColumn<Transaction, String>        colTxDescription;
    @FXML private TableColumn<Transaction, Transaction.TransactionType> colTxType;
    @FXML private Label                       emptyLabel;
    @FXML private Button                      addFeeButton;
    @FXML private Button                      saveButton;
    @FXML private Button                      deleteButton;

    // ── State ─────────────────────────────────────────────────────────────────
    private Member currentMember;
    private ResourceBundle bundle;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        this.bundle = rb;

        // Transactions tab setup (Phase 3 fills data; structure fixed here)
        colTxDate       .setCellValueFactory(new PropertyValueFactory<>("date"));
        colTxAmount     .setCellValueFactory(new PropertyValueFactory<>("amount"));
        colTxDescription.setCellValueFactory(new PropertyValueFactory<>("description"));
        colTxType       .setCellValueFactory(new PropertyValueFactory<>("type"));

        addFeeButton.setDisable(true); // re-enabled in Phase 3

        // Populate status and payment method combo boxes
        statusComboBox.setItems(FXCollections.observableArrayList(MemberStatus.values()));
        statusComboBox.setCellFactory(lv -> statusCell());
        statusComboBox.setButtonCell(statusCell());
        statusComboBox.setValue(MemberStatus.ACTIVE);

        paymentMethodComboBox.setItems(FXCollections.observableArrayList(PaymentMethod.values()));
        paymentMethodComboBox.setCellFactory(lv -> paymentCell());
        paymentMethodComboBox.setButtonCell(paymentCell());
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void setMember(Member member) {
        this.currentMember = member;
        if (member != null) {
            populateForm(member);
            // Show delete button only when the member has no transactions
            boolean deletable = memberService.canDelete(member.getId());
            deleteButton.setVisible(deletable);
            deleteButton.setManaged(deletable);
        } else {
            clearForm();
            deleteButton.setVisible(false);
            deleteButton.setManaged(false);
        }
        loadTransactions();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void populateForm(Member m) {
        memberNumberField     .setText(nullSafe(m.getMemberNumber()));
        fullNameField         .setText(nullSafe(m.getFullName()));
        phoneNumberField      .setText(nullSafe(m.getPhoneNumber()));
        emailField            .setText(nullSafe(m.getEmail()));
        addressField          .setText(nullSafe(m.getAddress()));
        postalCodeField       .setText(nullSafe(m.getPostalCode()));
        cityField             .setText(nullSafe(m.getCity()));
        placeField            .setText(nullSafe(m.getPlace()));
        parishField           .setText(nullSafe(m.getParish()));
        partnerNameField      .setText(nullSafe(m.getPartnerName()));
        partnerPhoneField     .setText(nullSafe(m.getPartnerPhone()));
        partnerEmailField     .setText(nullSafe(m.getPartnerEmail()));
        child1Field           .setText(nullSafe(m.getChild1()));
        child2Field           .setText(nullSafe(m.getChild2()));
        child3Field           .setText(nullSafe(m.getChild3()));
        child4Field           .setText(nullSafe(m.getChild4()));
        child5Field           .setText(nullSafe(m.getChild5()));
        applicationHolderField.setText(nullSafe(m.getApplicationHolder()));
        signingDatePicker     .setValue(m.getSigningDate());
        ibanField             .setText(nullSafe(m.getIban()));
        paymentMethodComboBox .setValue(m.getPaymentMethod());
        statusComboBox        .setValue(m.getStatus() != null ? m.getStatus() : MemberStatus.ACTIVE);
        categoryField         .setText(nullSafe(m.getCategory()));
        householdGroupField   .setText(nullSafe(m.getHouseholdGroup()));
    }

    private void clearForm() {
        memberNumberField     .setText("");
        fullNameField         .setText("");
        phoneNumberField      .setText("");
        emailField            .setText("");
        addressField          .setText("");
        postalCodeField       .setText("");
        cityField             .setText("");
        placeField            .setText("");
        parishField           .setText("");
        partnerNameField      .setText("");
        partnerPhoneField     .setText("");
        partnerEmailField     .setText("");
        child1Field           .setText("");
        child2Field           .setText("");
        child3Field           .setText("");
        child4Field           .setText("");
        child5Field           .setText("");
        applicationHolderField.setText("");
        signingDatePicker     .setValue(null);
        ibanField             .setText("");
        paymentMethodComboBox .setValue(null);
        statusComboBox        .setValue(MemberStatus.ACTIVE);
        categoryField         .setText("");
        householdGroupField   .setText("");
    }

    private void loadTransactions() {
        if (currentMember == null) {
            transactionsTable.setItems(FXCollections.emptyObservableList());
            emptyLabel.setVisible(true);
            txMemberNameLabel.setText("");
            txBalanceLabel.setText("");
            return;
        }
        txMemberNameLabel.setText(nullSafe(currentMember.getFullName()));
        List<Transaction> txs = memberService.getTransactionsByMember(currentMember.getId());
        transactionsTable.setItems(FXCollections.observableArrayList(txs));
        emptyLabel.setVisible(txs.isEmpty());
        // Balance label left blank for Phase 3
        txBalanceLabel.setText("");
    }

    private ListCell<MemberStatus> statusCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(MemberStatus s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); return; }
                String key = "members.status." + s.name().toLowerCase();
                setText(bundle != null && bundle.containsKey(key)
                        ? bundle.getString(key) : s.name());
            }
        };
    }

    private ListCell<PaymentMethod> paymentCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(PaymentMethod p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) { setText(null); return; }
                String key = switch (p) {
                    case DIRECT_DEBIT -> "members.payment.directDebit";
                    case TRANSFER     -> "members.payment.transfer";
                    case CASH         -> "members.payment.cash";
                };
                setText(bundle != null && bundle.containsKey(key)
                        ? bundle.getString(key) : p.name());
            }
        };
    }

    // ── FXML handlers ─────────────────────────────────────────────────────────

    @FXML
    private void onSave() {
        String fullName = fullNameField.getText().trim();
        if (fullName.isBlank()) {
            new Alert(Alert.AlertType.WARNING,
                    bundle != null ? bundle.getString("members.field.fullName") + " is required." : "Full name is required.")
                    .showAndWait();
            return;
        }

        if (currentMember == null) {
            currentMember = new Member();
        }

        currentMember.setFullName(fullName);
        currentMember.setPhoneNumber(trim(phoneNumberField.getText()));
        currentMember.setEmail(trim(emailField.getText()));
        currentMember.setAddress(trim(addressField.getText()));
        currentMember.setPostalCode(trim(postalCodeField.getText()));
        currentMember.setCity(trim(cityField.getText()));
        currentMember.setPlace(trim(placeField.getText()));
        currentMember.setParish(trim(parishField.getText()));
        currentMember.setPartnerName(trim(partnerNameField.getText()));
        currentMember.setPartnerPhone(trim(partnerPhoneField.getText()));
        currentMember.setPartnerEmail(trim(partnerEmailField.getText()));
        currentMember.setChild1(trim(child1Field.getText()));
        currentMember.setChild2(trim(child2Field.getText()));
        currentMember.setChild3(trim(child3Field.getText()));
        currentMember.setChild4(trim(child4Field.getText()));
        currentMember.setChild5(trim(child5Field.getText()));
        currentMember.setApplicationHolder(trim(applicationHolderField.getText()));
        currentMember.setSigningDate(signingDatePicker.getValue());
        currentMember.setIban(trim(ibanField.getText()));
        currentMember.setPaymentMethod(paymentMethodComboBox.getValue());
        currentMember.setStatus(statusComboBox.getValue() != null
                ? statusComboBox.getValue() : MemberStatus.ACTIVE);
        currentMember.setCategory(trim(categoryField.getText()));
        currentMember.setHouseholdGroup(trim(householdGroupField.getText()));

        saveButton.setDisable(true);
        try {
            memberService.save(currentMember);
            mainLayoutController.navigateTo("members");
        } catch (Exception e) {
            log.error("Failed to save member", e);
            saveButton.setDisable(false);
            new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait();
        }
    }

    @FXML
    private void onDelete() {
        if (currentMember == null) return;

        String confirmMsg = bundle != null
                ? bundle.getString("members.confirm.delete")
                : "Are you sure you want to permanently delete this member?";

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, confirmMsg,
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    memberService.delete(currentMember.getId());
                    mainLayoutController.navigateTo("members");
                } catch (IllegalStateException e) {
                    // Guarded in service too — belt-and-suspenders
                    String msg = bundle != null
                            ? bundle.getString("members.delete.hasTransactions")
                            : e.getMessage();
                    new Alert(Alert.AlertType.WARNING, msg).showAndWait();
                } catch (Exception e) {
                    log.error("Failed to delete member", e);
                    new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait();
                }
            }
        });
    }

    @FXML
    private void onCancel() {
        mainLayoutController.navigateTo("members");
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private static String nullSafe(String s) { return s != null ? s : ""; }
    private static String trim(String s)      { return s != null ? s.trim() : null; }
}
