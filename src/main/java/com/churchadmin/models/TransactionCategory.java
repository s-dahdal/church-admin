package com.churchadmin.models;

import com.churchadmin.utils.ChecksumService;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "transaction_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionCategory extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Transaction.TransactionType type;

    /** Seeded default categories cannot be deleted by admins */
    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean isDefault = false;

    @OneToMany(mappedBy = "category", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Transaction> transactions = new ArrayList<>();

    @Override
    protected String computeChecksum() {
        return ChecksumService.sha256(
                nullSafe(name),
                type != null ? type.name() : "",
                String.valueOf(isDefault)
        );
    }

    private static String nullSafe(String s) {
        return s != null ? s : "";
    }
}
