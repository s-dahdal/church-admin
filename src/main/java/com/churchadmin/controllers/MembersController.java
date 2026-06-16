package com.churchadmin.controllers;

import com.churchadmin.config.JavaFXConfig;
import com.churchadmin.models.Member;
import com.churchadmin.models.enums.MemberStatus;
import com.churchadmin.services.LocaleService;
import com.churchadmin.services.MemberService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;

import java.net.URL;
import java.text.MessageFormat;
import java.util.Comparator;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequiredArgsConstructor
public class MembersController implements Initializable {

    // ── Spring beans ──────────────────────────────────────────────────────────
    private final MemberService                  memberService;
    private final JavaFXConfig.FXMLLoaderFactory fxmlLoaderFactory;
    private final LocaleService                  localeService;
    private final MainLayoutController           mainLayoutController;

    // ── FXML fields ───────────────────────────────────────────────────────────
    @FXML private TableView<Member>          membersTable;
    @FXML private TableColumn<Member, String> colMemberNumber;
    @FXML private TableColumn<Member, String> colFullName;
    @FXML private TableColumn<Member, String> colParish;
    @FXML private TableColumn<Member, String> colCity;
    @FXML private TableColumn<Member, MemberStatus> colStatus;
    @FXML private TableColumn<Member, String> colPhoneNumber;
    @FXML private TextField                  searchField;
    @FXML private ComboBox<String>           statusFilter;
    @FXML private ComboBox<String>           parishFilter;
    @FXML private Label                      countLabel;

    // ── State ─────────────────────────────────────────────────────────────────
    private ObservableList<Member> allMembers;
    private ResourceBundle         bundle;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        this.bundle = rb;
        setupTableColumns();
        setupFilters(rb);
        loadMembers();
        wireListeners();
        membersTable.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                Member selected = membersTable.getSelectionModel().getSelectedItem();
                if (selected != null) openDetail(selected);
            }
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void setupTableColumns() {
        colMemberNumber.setCellValueFactory(new PropertyValueFactory<>("memberNumber"));
        colFullName    .setCellValueFactory(new PropertyValueFactory<>("fullName"));
        colParish      .setCellValueFactory(new PropertyValueFactory<>("parish"));
        colCity        .setCellValueFactory(new PropertyValueFactory<>("city"));
        colPhoneNumber .setCellValueFactory(new PropertyValueFactory<>("phoneNumber"));

        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(MemberStatus status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    String label = bundle.containsKey("members.status." + status.name().toLowerCase())
                            ? bundle.getString("members.status." + status.name().toLowerCase())
                            : status.name();
                    setText(label);
                    setStyle(status == MemberStatus.ACTIVE
                            ? "-fx-text-fill: #16A34A; -fx-font-weight: bold;"
                            : "-fx-text-fill: #6B7280;");
                }
            }
        });
    }

    private void setupFilters(ResourceBundle rb) {
        String all    = rb.getString("common.all");
        String active = rb.getString("members.status.active");
        String inactive = rb.getString("members.status.inactive");
        statusFilter.getItems().addAll(all, active, inactive);
        statusFilter.getSelectionModel().selectFirst();

        parishFilter.getItems().add(all);
        parishFilter.getSelectionModel().selectFirst();
    }

    private void loadMembers() {
        List<Member> members = memberService.findAll();
        allMembers = FXCollections.observableArrayList(members);
        membersTable.setItems(allMembers);
        updateParishFilter();
        updateCount(allMembers.size());
    }

    private void wireListeners() {
        searchField.textProperty().addListener((obs, old, val) -> applyFilters());
        statusFilter.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, val) -> applyFilters());
        parishFilter.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, val) -> applyFilters());
    }

    private void applyFilters() {
        String query         = searchField.getText();
        String statusSel     = statusFilter.getSelectionModel().getSelectedItem();
        String parishSel     = parishFilter.getSelectionModel().getSelectedItem();
        String all           = bundle.getString("common.all");
        String activeLabel   = bundle.getString("members.status.active");
        String inactiveLabel = bundle.getString("members.status.inactive");

        List<Member> filtered = allMembers.stream()
                .filter(m -> {
                    if (query != null && !query.isBlank()) {
                        String q = query.toLowerCase();
                        boolean nameMatch   = m.getFullName()     != null && m.getFullName().toLowerCase().contains(q);
                        boolean emailMatch  = m.getEmail()        != null && m.getEmail().toLowerCase().contains(q);
                        boolean numMatch    = m.getMemberNumber() != null && m.getMemberNumber().toLowerCase().contains(q);
                        boolean parish_match = m.getParish()      != null && m.getParish().toLowerCase().contains(q);
                        if (!nameMatch && !emailMatch && !numMatch && !parish_match) return false;
                    }
                    if (statusSel != null && !statusSel.equals(all)) {
                        boolean wantActive = statusSel.equals(activeLabel);
                        MemberStatus required = wantActive ? MemberStatus.ACTIVE : MemberStatus.INACTIVE;
                        if (m.getStatus() != required) return false;
                    }
                    if (parishSel != null && !parishSel.equals(all)) {
                        if (m.getParish() == null || !m.getParish().equals(parishSel)) return false;
                    }
                    return true;
                })
                .sorted(Comparator.comparing(m -> m.getFullName() != null ? m.getFullName() : ""))
                .collect(Collectors.toList());

        membersTable.setItems(FXCollections.observableArrayList(filtered));
        updateCount(filtered.size());
    }

    private void updateParishFilter() {
        String all = bundle.getString("common.all");
        List<String> parishes = allMembers.stream()
                .map(Member::getParish)
                .filter(p -> p != null && !p.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        String current = parishFilter.getValue();
        parishFilter.getItems().clear();
        parishFilter.getItems().add(all);
        parishFilter.getItems().addAll(parishes);
        if (current != null && parishFilter.getItems().contains(current)) {
            parishFilter.setValue(current);
        } else {
            parishFilter.getSelectionModel().selectFirst();
        }
    }

    private void updateCount(int count) {
        if (bundle != null) {
            String pattern = bundle.getString("members.count");
            countLabel.setText(MessageFormat.format(pattern, count));
        }
    }

    private void openDetail(Member member) {
        try {
            FXMLLoader loader = fxmlLoaderFactory.create();
            URL resource = getClass().getResource("/fxml/MemberDetail.fxml");
            if (resource == null) {
                log.error("MemberDetail.fxml not found");
                return;
            }
            loader.setLocation(resource);
            loader.setResources(localeService.getBundle());
            Node view = loader.load();
            MemberDetailController ctrl = loader.getController();
            ctrl.setMember(member);
            mainLayoutController.showView(view);
        } catch (Exception e) {
            log.error("Failed to open member detail", e);
        }
    }

    // ── FXML handlers ─────────────────────────────────────────────────────────

    @FXML
    private void onAddMember() {
        openDetail(null);
    }
}
