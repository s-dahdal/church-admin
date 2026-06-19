package com.churchadmin.models;

import com.churchadmin.models.enums.TransactionType;
import com.churchadmin.utils.ChecksumService;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction extends BaseEntity {

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    private String description;

    /**
     * Nullable — when null this is a general church transaction.
     * When set, it is a member-linked contribution / membership fee.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private TransactionCategory category;

    @Override
    protected String computeChecksum() {
        return ChecksumService.sha256(
                date != null ? date.toString() : "",
                amount != null ? amount.toPlainString() : "",
                type != null ? type.name() : "",
                nullSafe(description),
                member != null ? member.getId() : "",
                category != null ? category.getId() : ""
        );
    }

    private static String nullSafe(String s) {
        return s != null ? s : "";
    }

}
