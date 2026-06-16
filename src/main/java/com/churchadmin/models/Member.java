package com.churchadmin.models;

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

    @Column(name = "full_name", nullable = false)
    private String fullName;

    private String phone;

    private String email;

    private String address;

    @Column(name = "join_date")
    private LocalDate joinDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberStatus status = MemberStatus.ACTIVE;

    /** Free-text category label (e.g. "Youth", "Elder", "Deacon") */
    private String category;

    /** Groups members into a household (family) unit */
    @Column(name = "household_group")
    private String householdGroup;

    /** Transactions linked to this member (membership fees / contributions) */
    @OneToMany(mappedBy = "member", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Builder.Default
    private List<Transaction> transactions = new ArrayList<>();

    @Override
    protected String computeChecksum() {
        return ChecksumService.sha256(
                nullSafe(fullName),
                nullSafe(phone),
                nullSafe(email),
                nullSafe(address),
                joinDate != null ? joinDate.toString() : "",
                status != null ? status.name() : "",
                nullSafe(category),
                nullSafe(householdGroup)
        );
    }

    private static String nullSafe(String s) {
        return s != null ? s : "";
    }

    public enum MemberStatus {
        ACTIVE, INACTIVE
    }
}
