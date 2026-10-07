package com.netbanking.forex.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.util.List;

class DemoForexRatesTest {
    private final DemoForexRates rates =
            new DemoForexRates("", DemoForexRates.DEFAULT_REFERENCE_RATES, "2026-10-07T09:00:00Z");

    @Test
    void quotesEveryPairOfTheEightCurrencies() {
        List<String> codes = rates.currencies().stream().map(DemoForexRates.Currency::code).toList();
        assertThat(codes).containsExactly("INR", "USD", "EUR", "GBP", "JPY", "AUD", "CAD", "SGD");
        for (String from : codes)
            for (String to : codes)
                assertThat(rates.rate(from, to).signum()).isPositive();
        assertThat(rates.currencies().get(4).name()).isEqualTo("Japanese Yen");
        assertThat(rates.asOf()).hasToString("2026-10-07T09:00:00Z");
    }

    @Test
    void derivesCrossRatesFromInrReferenceValues() {
        assertThat(rates.rate("USD", "INR")).isEqualByComparingTo("85.00000000");
        assertThat(rates.rate("INR", "USD")).isEqualByComparingTo("0.01176471");
        assertThat(rates.rate("JPY", "GBP")).isEqualByComparingTo("0.00527778");
        assertThat(rates.rate("EUR", "USD")).isEqualByComparingTo("1.08823529");
    }

    @Test
    void anExplicitPairOverridesOneDirectionOnly() {
        DemoForexRates overridden = new DemoForexRates("USD/INR=84.90000000", DemoForexRates.DEFAULT_REFERENCE_RATES, "");

        assertThat(overridden.rate("USD", "INR")).isEqualByComparingTo("84.9");
        assertThat(overridden.rate("INR", "USD")).isEqualByComparingTo("0.01176471");
    }

    @Test
    void rejectsUnknownCurrenciesAndConfigurationWithoutInr() {
        assertThatThrownBy(() -> rates.rate("USD", "CHF")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DemoForexRates("", "USD=85", "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DemoForexRates("", "INR=1,XXY=2", "")).isInstanceOf(IllegalArgumentException.class);
    }
}
