package com.netbanking.forex.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ForexRatesResponse(
        String baseCurrency, String rateSource, Instant asOf, List<Currency> currencies) {

    /** {@code inrValue} is the INR value of one unit of the currency. */
    public record Currency(String code, String name, int fractionDigits, BigDecimal inrValue) {}
}
