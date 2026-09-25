package com.netbanking.biller.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "biller")
public class Biller {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "biller_id")
    private Long billerId;

    @Column(name = "biller_code")
    private String billerCode;

    @Column(name = "biller_name")
    private String billerName;

    private String category;

    @Column(name = "reference_label")
    private String referenceLabel;

    @Column(name = "min_amount")
    private BigDecimal minAmount;

    @Column(name = "max_amount")
    private BigDecimal maxAmount;

    @Column(name = "is_active", nullable = false, length = 1)
    private Character isActive;

    @Column(name = "reference_pattern", length = 200)
    private String referencePattern;

    protected Biller() {
    }

    public Long getBillerId() {
        return billerId;
    }

    public String getBillerCode() {
        return billerCode;
    }

    public String getBillerName() {
        return billerName;
    }

    public String getCategory() {
        return category;
    }

    public String getReferenceLabel() {
        return referenceLabel;
    }

    public BigDecimal getMinAmount() {
        return minAmount;
    }

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public boolean isActive() {
        return isActive != null && isActive == 'Y';
    }

    public void validateReference(String reference) {
        String normalized = reference == null ? "" : reference.strip();
        String pattern = referencePattern == null || referencePattern.isBlank()
                ? "^[A-Za-z0-9][A-Za-z0-9 /._-]{4,149}$" : referencePattern;
        if (!normalized.matches(pattern)) {
            throw new IllegalArgumentException("Bill reference is invalid for this biller.");
        }
    }
}
