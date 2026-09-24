package com.netbanking.biller.domain;
import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity @Table(name = "biller")
public class Biller {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "biller_id") private Long billerId;
    @Column(name = "biller_code") private String billerCode; @Column(name = "biller_name") private String billerName;
    private String category; @Column(name = "reference_label") private String referenceLabel;
    @Column(name = "min_amount") private BigDecimal minAmount; @Column(name = "max_amount") private BigDecimal maxAmount;
    @Column(name = "is_active", columnDefinition = "CHAR(1)") private String isActive;
    protected Biller() { }
    public Long getBillerId() { return billerId; } public String getBillerCode() { return billerCode; } public String getBillerName() { return billerName; }
    public String getCategory() { return category; } public String getReferenceLabel() { return referenceLabel; } public BigDecimal getMinAmount() { return minAmount; } public BigDecimal getMaxAmount() { return maxAmount; }
    public boolean isActive() { return isActive != null && "Y".equalsIgnoreCase(isActive.trim()); }
    public void validateReference(String reference) {
        String normalized = reference == null ? "" : reference.strip();
        boolean valid = switch (billerCode) {
            case "MOBILE" -> normalized.matches("[0-9]{10}");
            case "ELECTRICITY" -> normalized.matches("[0-9]{6,20}");
            case "WATER" -> normalized.matches("[A-Za-z0-9-]{5,30}");
            default -> normalized.matches("[A-Za-z0-9][A-Za-z0-9 /-]{2,149}");
        };
        if (!valid) throw new IllegalArgumentException("Bill reference does not match the selected biller.");
    }
}
