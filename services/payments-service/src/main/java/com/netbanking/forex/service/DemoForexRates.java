package com.netbanking.forex.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/** Operator-supplied demonstration rates. No market-data or executable-rate claim is made. */
@Component
public class DemoForexRates {
    private final Map<String, BigDecimal> rates;

    public DemoForexRates(@Value("${app.forex.demo-rates:}") String configured) {
        Map<String, BigDecimal> parsed = new HashMap<>();
        if (!configured.isBlank()) {
            for (String item : configured.split(",")) {
                String[] pair = item.strip().split("=", -1);
                if (pair.length != 2 || !pair[0].matches("[A-Z]{3}/[A-Z]{3}"))
                    throw new IllegalArgumentException("Invalid forex demo-rate pair: " + item);
                BigDecimal rate = new BigDecimal(pair[1]);
                if (rate.signum() <= 0 || rate.scale() > 8 || parsed.putIfAbsent(pair[0], rate) != null)
                    throw new IllegalArgumentException("Forex demo rates must be unique positive values with at most 8 decimals.");
            }
        }
        this.rates = Map.copyOf(parsed);
    }

    public BigDecimal rate(String source, String destination) {
        BigDecimal value = rates.get(source + "/" + destination);
        if (value == null)
            throw new IllegalArgumentException("This currency pair has no configured demonstration rate.");
        return value;
    }
}
