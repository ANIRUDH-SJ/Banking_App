package com.netbanking.card.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reducing-balance EMI: P x r x (1 + r)^n / ((1 + r)^n - 1), with r the monthly rate. */
@Component
public class EmiCalculator {
    static final String DEFAULT_RATES = "3:13.00,6:14.00,9:15.00";

    private final Map<Integer, BigDecimal> annualRates;

    public EmiCalculator(@Value("${app.cards.emi.rates:" + DEFAULT_RATES + "}") String configured) {
        Map<Integer, BigDecimal> parsed = new LinkedHashMap<>();
        for (String item : configured.split(",")) {
            String[] pair = item.strip().split(":", -1);
            int tenure = Integer.parseInt(pair[0].strip());
            BigDecimal rate = new BigDecimal(pair[1].strip());
            if (!List.of(3, 6, 9).contains(tenure) || rate.signum() < 0 || rate.compareTo(new BigDecimal("60")) > 0
                    || parsed.putIfAbsent(tenure, rate) != null)
                throw new IllegalArgumentException("EMI rates must be unique 3, 6 or 9 month tenures at 0-60% a year.");
        }
        this.annualRates = Map.copyOf(parsed);
    }

    public List<Option> options(BigDecimal principal) {
        return annualRates.keySet().stream().sorted().map(tenure -> option(principal, tenure)).toList();
    }

    public Option option(BigDecimal principal, int tenureMonths) {
        BigDecimal annual = annualRates.get(tenureMonths);
        if (annual == null)
            throw new IllegalArgumentException("Choose a tenure of 3, 6 or 9 months.");
        BigDecimal instalment;
        if (annual.signum() == 0) {
            instalment = principal.divide(BigDecimal.valueOf(tenureMonths), 2, RoundingMode.CEILING);
        } else {
            MathContext mc = MathContext.DECIMAL64;
            BigDecimal r = annual.divide(new BigDecimal("1200"), mc);
            BigDecimal growth = BigDecimal.ONE.add(r).pow(tenureMonths, mc);
            instalment = principal.multiply(r, mc).multiply(growth, mc)
                    .divide(growth.subtract(BigDecimal.ONE), mc)
                    .setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal total = instalment.multiply(BigDecimal.valueOf(tenureMonths));
        return new Option(tenureMonths, annual, instalment, total.subtract(principal).max(BigDecimal.ZERO), total);
    }

    public record Option(
            int tenureMonths,
            BigDecimal annualInterestRate,
            BigDecimal monthlyInstalment,
            BigDecimal totalInterest,
            BigDecimal totalPayable) {}
}
