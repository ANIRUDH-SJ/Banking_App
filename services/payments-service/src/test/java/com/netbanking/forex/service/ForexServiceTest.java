package com.netbanking.forex.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.netbanking.contracts.AccountSnapshot;
import com.netbanking.contracts.ForexLedgerReceipt;
import com.netbanking.discovery.LedgerClient;
import com.netbanking.discovery.OtpClient;
import com.netbanking.forex.api.CreateForexQuoteRequest;
import com.netbanking.forex.api.CreateForexConversionRequest;
import com.netbanking.forex.api.ForexQuoteResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

class ForexServiceTest {
    final ForexStore store = mock(ForexStore.class);
    final LedgerClient ledger = mock(LedgerClient.class);
    final OtpClient otp = mock(OtpClient.class);
    final ForexService service = new ForexService(store,
            new DemoForexRates("INR/USD=0.01176471,USD/INR=85.00000000"), ledger, otp, 120);

    @Test
    void quoteUsesOwnedAccountsAndConfiguredSnapshotRate() {
        when(ledger.account(7L, 1L)).thenReturn(account(1L, "INR"));
        when(ledger.account(7L, 2L)).thenReturn(account(2L, "USD"));
        when(store.saveQuote(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var quote = service.quote(7L,
                new CreateForexQuoteRequest(1L, 2L, new BigDecimal("850.0000")));

        assertThat(quote.rateSource()).isEqualTo("DEMO_CONFIGURED");
        assertThat(quote.exchangeRate()).isEqualByComparingTo("0.01176471");
        assertThat(quote.destinationAmount()).isEqualByComparingTo("10.0000");
        assertThat(quote.expiresAt().getOffset()).isEqualTo(ZoneOffset.UTC);
        verify(store).saveQuote(any());
    }

    @Test
    void rejectsUnsupportedOrSameCurrencyPair() {
        when(ledger.account(7L, 1L)).thenReturn(account(1L, "INR"));
        when(ledger.account(7L, 2L)).thenReturn(account(2L, "CHF"));
        assertThatThrownBy(() -> service.quote(7L,
                new CreateForexQuoteRequest(1L, 2L, new BigDecimal("100"))))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(store);
    }

    @Test
    void completedIdempotentRequestReturnsReceiptWithoutAnotherDebit() {
        var quote = quote();
        var conversion = new ForexStore.Conversion(9L, quote, "request-1",
                com.netbanking.contracts.RequestFingerprint.of("FOREX_REQUEST", 7L, "quote-1"),
                "operation-1", "COMPLETED", "DR-1", "CR-1");
        when(store.byRequest(7L, "request-1")).thenReturn(conversion);

        var result = service.convert(7L,
                new CreateForexConversionRequest("quote-1", "request-1", "challenge", "123456"));
        assertThat(result.creditReference()).isEqualTo("CR-1");
        verifyNoInteractions(ledger, otp);
    }

    @Test
    void authorizedRetryUsesSameLedgerOperation() {
        var quote = quote();
        var conversion = new ForexStore.Conversion(9L, quote, "request-1",
                com.netbanking.contracts.RequestFingerprint.of("FOREX_REQUEST", 7L, "quote-1"),
                "operation-1", "AUTHORIZED", null, null);
        when(store.byRequest(7L, "request-1")).thenReturn(conversion);
        when(store.byOperation("operation-1")).thenReturn(conversion);
        when(ledger.convert(any())).thenReturn(new ForexLedgerReceipt(1L, "DR-1", 2L, "CR-1",
                new BigDecimal("850"), new BigDecimal("10")));
        when(store.complete(eq("operation-1"), any())).thenReturn(conversion.response());

        service.convert(7L,
                new CreateForexConversionRequest("quote-1", "request-1", "challenge", "123456"));

        verify(ledger).convert(argThat(command -> command.operationId().equals("operation-1")));
        verifyNoInteractions(otp);
    }

    private static AccountSnapshot account(Long id, String currency) {
        return new AccountSnapshot(id, "1234567890", currency, "ACTIVE", new BigDecimal("1000"));
    }

    private static ForexStore.Quote quote() {
        return new ForexStore.Quote(new ForexQuoteResponse("quote-1", 1L, 2L, "INR", "USD",
                new BigDecimal("850"), new BigDecimal("10"), new BigDecimal("0.01176471"),
                "DEMO_CONFIGURED", LocalDateTime.now(ZoneOffset.UTC).plusMinutes(2).atOffset(ZoneOffset.UTC)),
                7L, LocalDateTime.now(ZoneOffset.UTC));
    }

    @Test
    void previewsAnyPairAndRoundsToTheDestinationMinorUnit() {
        var preview = service.preview("usd", "JPY", new BigDecimal("100"));

        assertThat(preview.fromCurrency()).isEqualTo("USD");
        assertThat(preview.exchangeRate()).isEqualByComparingTo("149.12280702");
        assertThat(preview.convertedAmount()).isEqualByComparingTo("14912");
        assertThat(preview.convertedAmount().scale()).isZero();
        assertThat(preview.inverseRate()).isEqualByComparingTo("0.00670588");
        assertThat(service.preview("GBP", "INR", new BigDecimal("2.5")).convertedAmount()).isEqualByComparingTo("270.00");
        assertThat(service.rates().currencies()).hasSize(8);
        verifyNoInteractions(store, ledger, otp);
    }

    @Test
    void previewRejectsUnsupportedCurrenciesAndAmounts() {
        assertThatThrownBy(() -> service.preview("USD", "CHF", BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.preview("USD", "INR", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
