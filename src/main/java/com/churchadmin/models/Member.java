package com.churchadmin.models;

import com.churchadmin.models.enums.MemberStatus;
import com.churchadmin.models.enums.PaymentMethod;
import com.churchadmin.utils.ChecksumService;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Member extends BaseEntity {

    // ── Identity ──────────────────────────────────────────────────────────────
    @Column(name = "member_number", unique = true, nullable = false)
    private String memberNumber;

    // ── Personal ──────────────────────────────────────────────────────────────
    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "phone_number")
    private String phoneNumber;

    private String email;

    // ── Address ───────────────────────────────────────────────────────────────
    private String address;

    @Column(name = "postal_code")
    private String postalCode;

    private String city;

    private String place;

    private String parish;

    // ── Partner ───────────────────────────────────────────────────────────────
    @Column(name = "partner_name")
    private String partnerName;

    @Column(name = "partner_phone")
    private String partnerPhone;

    @Column(name = "partner_email")
    private String partnerEmail;

    // ── Children ──────────────────────────────────────────────────────────────
    private String child1;
    private String child2;
    private String child3;
    private String child4;
    private String child5;

    // ── Membership application ────────────────────────────────────────────────
    @Column(name = "application_holder")
    private String applicationHolder;

    @Column(name = "signing_date")
    private LocalDate signingDate;

    // ── Financial ─────────────────────────────────────────────────────────────
    private String iban;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    // ── Status ────────────────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MemberStatus status = MemberStatus.ACTIVE;

    private String category;

    @Column(name = "household_group")
    private String householdGroup;

    // ── Relations ─────────────────────────────────────────────────────────────
    @OneToMany(mappedBy = "member", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Builder.Default
    private List<Transaction> transactions = new ArrayList<>();

    // ── Integrity ─────────────────────────────────────────────────────────────
    @Override
    protected String computeChecksum() {
        return ChecksumService.sha256(
                n(memberNumber),
                n(fullName),
                n(phoneNumber),
                n(email),
                n(address),
                n(postalCode),
                n(city),
                n(place),
                n(parish),
                n(partnerName),
                n(partnerPhone),
                n(partnerEmail),
                n(child1),
                n(child2),
                n(child3),
                n(child4),
                n(child5),
                n(applicationHolder),
                signingDate != null ? signingDate.toString() : "",
                n(iban),
                paymentMethod != null ? paymentMethod.name() : "",
                status != null ? status.name() : "",
                n(category),
                n(householdGroup)
        );
    }

    private static String n(String s) {
        return s != null ? s : "";
    }
}
