package com.netbanking.forex.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Operator-supplied demonstration rates. No market-data or executable-rate claim is made.
 *
 * <p>Each supported currency has a reference value in INR; the rate between any two currencies is
 * the ratio of their reference values. An explicit pair in {@code app.forex.demo-rates} overrides
 * the derived rate for that direction only.
 */
@Component
public class DemoForexRates {
    public static final String SOURCE = "DEMO_CONFIGURED";
    static final String DEFAULT_REFERENCE_RATES =
            "INR=1,USD=85.00,EUR=92.50,GBP=108.00,JPY=0.5700,AUD=56.20,CAD=62.40,SGD=63.80";
    private static final int RATE_SCALE = 8;

    private final Map<String, BigDecimal> pairs;
    private final Map<String, BigDecimal> reference;
    private final Instant asOf;

    public DemoForexRates(String configured) {
        this(configured, DEFAULT_REFERENCE_RATES, "");
    }

    @Autowired
    public DemoForexRates(
            @Value("${app.forex.demo-rates:}") String configured,
            @Value("${app.forex.reference-rates:" + DEFAULT_REFERENCE_RATES + "}") String referenceRates,
            @Value("${app.forex.rates-as-of:}") String asOf) {
        this.pairs = Map.copyOf(parsePairs(configured));
        this.reference = parseReference(referenceRates);
        this.asOf = asOf == null || asOf.isBlank() ? Instant.now() : Instant.parse(asOf.strip());
    }

    public BigDecimal rate(String source, String destination) {
        BigDecimal explicit = pairs.get(source + "/" + destination);
        if (explicit != null) return explicit;
        BigDecimal from = reference.get(source);
        BigDecimal to = reference.get(destination);
        if (from == null || to == null)
            throw new IllegalArgumentException("This currency pair has no configured demonstration rate.");
        if (source.equals(destination)) return BigDecimal.ONE.setScale(RATE_SCALE);
        return from.divide(to, MathContext.DECIMAL64).setScale(RATE_SCALE, RoundingMode.HALF_EVEN);
    }

    /** Supported currencies in configuration order, each with its INR reference value. */
    public List<Currency> currencies() {
        return reference.entrySet().stream()
                .map(
                        entry -> {
                            var currency = java.util.Currency.getInstance(entry.getKey());
                            return new Currency(
                                    entry.getKey(),
                                    currency.getDisplayName(Locale.ENGLISH),
                                    currency.getDefaultFractionDigits(),
                                    entry.getValue());
                        })
                .toList();
    }

    public boolean supports(String currencyCode) {
        return reference.containsKey(currencyCode);
    }

    public Instant asOf() {
        return asOf;
    }

    public record Currency(String code, String name, int fractionDigits, BigDecimal inrValue) {}

    private static Map<String, BigDecimal> parsePairs(String configured) {
        Map<String, BigDecimal> parsed = new HashMap<>();
        if (configured == null || configured.isBlank()) return parsed;
        for (String item : configured.split(",")) {
            String[] pair = item.strip().split("=", -1);
            if (pair.length != 2 || !pair[0].matches("[A-Z]{3}/[A-Z]{3}"))
                throw new IllegalArgumentException("Invalid forex demo-rate pair: " + item);
            BigDecimal rate = new BigDecimal(pair[1]);
            if (rate.signum() <= 0 || rate.scale() > RATE_SCALE || parsed.putIfAbsent(pair[0], rate) != null)
                throw new IllegalArgumentException("Forex demo rates must be unique positive values with at most 8 decimals.");
        }
        return parsed;
    }

    private static Map<String, BigDecimal> parseReference(String configured) {
        Map<String, BigDecimal> parsed = new LinkedHashMap<>();
        for (String item : configured.split(",")) {
            String[] entry = item.strip().split("=", -1);
            if (entry.length != 2 || !entry[0].matches("[A-Z]{3}"))
                throw new IllegalArgumentException("Invalid forex reference rate: " + item);
            try {
                java.util.Currency.getInstance(entry[0]);
            } catch (IllegalArgumentException unknown) {
                throw new IllegalArgumentException("Unknown currency in forex reference rates: " + entry[0]);
            }
            BigDecimal value = new BigDecimal(entry[1]);
            if (value.signum() <= 0 || parsed.putIfAbsent(entry[0], value) != null)
                throw new IllegalArgumentException("Forex reference rates must be unique positive values.");
        }
        if (!BigDecimal.ONE.equals(parsed.getOrDefault("INR", BigDecimal.ZERO).stripTrailingZeros()))
            throw new IllegalArgumentException("Forex reference rates must include INR=1.");
        return java.util.Collections.unmodifiableMap(parsed);
    }
}
