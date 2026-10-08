package com.netbanking.forex.service;

import com.netbanking.common.api.PagedResponse;
import com.netbanking.common.exception.ConflictException;
import com.netbanking.contracts.*;
import com.netbanking.discovery.LedgerClient;
import com.netbanking.discovery.OtpClient;
import com.netbanking.forex.api.*;
import com.netbanking.payment.api.OtpChallengeResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

@Service
public class ForexService {
    private final ForexStore store;
    private final DemoForexRates rates;
    private final LedgerClient ledger;
    private final OtpClient otp;
    private final Clock clock = Clock.systemUTC();
    private final long quoteSeconds;

    public ForexService(ForexStore store, DemoForexRates rates, LedgerClient ledger, OtpClient otp,
            @Value("${app.forex.quote-seconds:120}") long quoteSeconds) {
        if (quoteSeconds < 30 || quoteSeconds > 600)
            throw new IllegalArgumentException("Forex quote lifetime must be between 30 and 600 seconds.");
        this.store = store;
        this.rates = rates;
        this.ledger = ledger;
        this.otp = otp;
        this.quoteSeconds = quoteSeconds;
    }

    public ForexQuoteResponse quote(Long userId, CreateForexQuoteRequest request) {
        if (request.sourceAccountId().equals(request.destinationAccountId()))
            throw new IllegalArgumentException("Source and destination accounts must differ.");
        AccountSnapshot source = activeAccount(userId, request.sourceAccountId());
        AccountSnapshot destination = activeAccount(userId, request.destinationAccountId());
        if (source.currencyCode().equals(destination.currencyCode()))
            throw new IllegalArgumentException("Forex requires accounts with different currencies.");
        BigDecimal rate = rates.rate(source.currencyCode(), destination.currencyCode());
        BigDecimal amount = request.sourceAmount();
        BigDecimal converted = convertAmount(amount, rate, destination.currencyCode());
        if (converted.signum() <= 0 || converted.precision() - converted.scale() > 15)
            throw new IllegalArgumentException("Converted amount is outside the supported range.");
        LocalDateTime now = LocalDateTime.now(clock);
        var response = new ForexQuoteResponse(UUID.randomUUID().toString(),
                source.accountId(), destination.accountId(), source.currencyCode(),
                destination.currencyCode(), amount, converted, rate,
                DemoForexRates.SOURCE, now.plusSeconds(quoteSeconds).atOffset(ZoneOffset.UTC));
        return store.saveQuote(new ForexStore.Quote(response, userId, now)).response();
    }

    public ForexRatesResponse rates() {
        return new ForexRatesResponse(
                "INR",
                DemoForexRates.SOURCE,
                rates.asOf(),
                rates.currencies().stream()
                        .map(c -> new ForexRatesResponse.Currency(
                                c.code(), c.name(), c.fractionDigits(), c.inrValue()))
                        .toList());
    }

    /** Indicative conversion between any two supported currencies; nothing is booked. */
    public ForexConversionPreview preview(String from, String to, BigDecimal amount) {
        String source = code(from);
        String destination = code(to);
        if (amount == null || amount.signum() <= 0 || amount.scale() > 4
                || amount.precision() - amount.scale() > 15)
            throw new IllegalArgumentException("Enter an amount greater than zero with at most 4 decimals.");
        BigDecimal rate = rates.rate(source, destination);
        return new ForexConversionPreview(
                source, destination, amount, rate, rates.rate(destination, source),
                convertAmount(amount, rate, destination), DemoForexRates.SOURCE, rates.asOf());
    }

    private String code(String value) {
        String code = value == null ? "" : value.strip().toUpperCase(java.util.Locale.ROOT);
        if (!rates.supports(code))
            throw new IllegalArgumentException("Currency " + code + " is not supported for conversion.");
        return code;
    }

    private static BigDecimal convertAmount(BigDecimal amount, BigDecimal rate, String currency) {
        int digits = Math.min(4, Math.max(0, java.util.Currency.getInstance(currency).getDefaultFractionDigits()));
        return amount.multiply(rate).setScale(digits, RoundingMode.HALF_EVEN);
    }

    public OtpChallengeResponse challenge(Long userId, String quoteId) {
        var quote = currentQuote(userId, quoteId);
        var issued = otp.issue(userId, "FOREX_CONVERSION", digest(quote));
        return new OtpChallengeResponse(issued.challengeId(), "OTP_SENT");
    }

    public ForexConversionResponse convert(Long userId, CreateForexConversionRequest request) {
        String fingerprint = RequestFingerprint.of("FOREX_REQUEST", userId, request.quoteId());
        ForexStore.Conversion operation = store.byRequest(userId, request.idempotencyKey());
        if (operation == null) {
            var quote = currentQuote(userId, request.quoteId());
            activeAccount(userId, quote.response().sourceAccountId());
            activeAccount(userId, quote.response().destinationAccountId());
            try {
                operation = store.create(quote, request.idempotencyKey(), fingerprint,
                        UUID.randomUUID().toString());
            } catch (DataIntegrityViolationException concurrent) {
                operation = store.byRequest(userId, request.idempotencyKey());
                if (operation == null)
                    throw new ConflictException("Forex quote has already been used.");
            }
        }
        if (!operation.fingerprint().equals(fingerprint))
            throw new ConflictException("Idempotency key was used for a different conversion.");
        if ("COMPLETED".equals(operation.state())) return operation.response();
        if ("FAILED".equals(operation.state()))
            throw new ConflictException("Conversion failed. Request a new quote and key.");
        if ("AWAITING_OTP".equals(operation.state())) {
            if (!OffsetDateTime.now(clock).isBefore(operation.quote().response().expiresAt()))
                throw new ConflictException("Forex quote has expired.");
            otp.authorize(operation.operationId(), userId, request.otpChallengeId(),
                    request.otpCode(), "FOREX_CONVERSION", digest(operation.quote()));
            store.authorized(operation.operationId());
        }
        return settle(store.byOperation(operation.operationId()));
    }

    public ForexConversionResponse settle(ForexStore.Conversion operation) {
        var q = operation.quote().response();
        ForexLedgerReceipt receipt;
        try {
            receipt = ledger.convert(new ForexLedgerCommand(operation.operationId(),
                    operation.quote().userId(), q.sourceAccountId(), q.destinationAccountId(),
                    q.sourceAmount(), q.destinationAmount(), q.sourceCurrency(),
                    q.destinationCurrency()));
        } catch (ResponseStatusException rejected) {
            if (Set.of(400, 403, 404, 409, 422).contains(rejected.getStatusCode().value()))
                store.failed(operation.operationId());
            throw rejected;
        }
        return store.complete(operation.operationId(), receipt);
    }

    public ForexConversionResponse get(Long userId, Long conversionId) {
        return store.owned(userId, conversionId);
    }

    public PagedResponse<ForexConversionResponse> history(Long userId, int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new IllegalArgumentException("Page must be non-negative and size between 1 and 100.");
        return PagedResponse.from(store.history(userId, page, size));
    }

    private ForexStore.Quote currentQuote(Long userId, String quoteId) {
        var quote = store.quote(userId, quoteId);
        if (!OffsetDateTime.now(clock).isBefore(quote.response().expiresAt()))
            throw new ConflictException("Forex quote has expired.");
        return quote;
    }

    private AccountSnapshot activeAccount(Long userId, Long accountId) {
        var account = ledger.account(userId, accountId);
        if (!"ACTIVE".equals(account.status()))
            throw new ConflictException("Both forex accounts must be active.");
        return account;
    }

    private static String digest(ForexStore.Quote quote) {
        var q = quote.response();
        return RequestFingerprint.of("FOREX_CONVERSION", quote.userId(), q.quoteId(),
                q.sourceAccountId(), q.destinationAccountId(), q.sourceAmount(),
                q.destinationAmount(), q.sourceCurrency(), q.destinationCurrency(), q.exchangeRate());
    }
}
