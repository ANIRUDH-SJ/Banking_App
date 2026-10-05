package com.netbanking.deposit.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Component
public class DepositPricing {
    private final BigDecimal fixedRate;
    private final BigDecimal recurringRate;

    public DepositPricing(@Value("${app.deposits.fd-rate-percent:6.50}") BigDecimal fixedRate,
            @Value("${app.deposits.rd-rate-percent:6.00}") BigDecimal recurringRate) {
        this.fixedRate = checked(fixedRate);
        this.recurringRate = checked(recurringRate);
    }

    private static BigDecimal checked(BigDecimal rate) {
        if (rate.signum() < 0 || rate.compareTo(new BigDecimal("25")) > 0)
            throw new IllegalArgumentException("Deposit rate must be between 0 and 25 percent.");
        return rate;
    }

    public BigDecimal rate(String kind) {
        return "FD".equals(kind) ? fixedRate : recurringRate;
    }

    public BigDecimal accrued(BigDecimal amount, BigDecimal annualRatePercent,
            LocalDate paidOn, LocalDate maturityOn) {
        long days = Math.max(0, ChronoUnit.DAYS.between(paidOn, maturityOn));
        return amount.multiply(annualRatePercent)
                .multiply(BigDecimal.valueOf(days))
                .divide(new BigDecimal("36500"), 8, RoundingMode.HALF_EVEN);
    }

    public BigDecimal estimate(String kind, BigDecimal amount, int months,
            BigDecimal rate, LocalDate start) {
        LocalDate maturity = start.plusMonths(months);
        BigDecimal total = BigDecimal.ZERO;
        int contributions = "FD".equals(kind) ? 1 : months;
        for (int n = 0; n < contributions; n++) {
            LocalDate payment = start.plusMonths(n);
            total = total.add(amount).add(accrued(amount, rate, payment, maturity));
        }
        return total.setScale(2, RoundingMode.HALF_EVEN);
    }
}
