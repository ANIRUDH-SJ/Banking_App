package com.netbanking.biller.provider;

import com.netbanking.common.exception.ConflictException;
import com.netbanking.contracts.RequestFingerprint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@ConditionalOnProperty(
        name = "app.biller-payment.provider",
        havingValue = "mock",
        matchIfMissing = true)
public class MockBillerPaymentAdapter implements BillerPaymentAdapter {
    private static final Logger log = LoggerFactory.getLogger(MockBillerPaymentAdapter.class);

    private final ConcurrentMap<String, CollectionState> collections = new ConcurrentHashMap<>();
    private final int failuresBeforeSuccess;
    private final String rejectedReferencePrefix;

    public MockBillerPaymentAdapter(
            @Value("${app.biller-payment.mock.failures-before-success:0}")
                    int failuresBeforeSuccess,
            @Value("${app.biller-payment.mock.rejected-reference-prefix:}")
                    String rejectedReferencePrefix) {
        if (failuresBeforeSuccess < 0) {
            throw new IllegalArgumentException("Mock biller failures must not be negative.");
        }
        this.failuresBeforeSuccess = failuresBeforeSuccess;
        this.rejectedReferencePrefix = rejectedReferencePrefix.strip();
    }

    @Override
    public BillerPaymentReceipt collect(BillerPaymentCommand command) {
        String fingerprint =
                RequestFingerprint.of(
                        command.billerCode(),
                        command.customerReference(),
                        command.amount(),
                        command.currencyCode());
        CollectionState state =
                collections.compute(
                        command.operationId(),
                        (key, current) -> {
                            if (current != null && !current.fingerprint.equals(fingerprint)) {
                                throw new ConflictException(
                                        "Biller payment key was used for different details.");
                            }
                            return current == null ? new CollectionState(fingerprint) : current;
                        });
        return state.collect(command, failuresBeforeSuccess, rejectedReferencePrefix);
    }

    private static final class CollectionState {
        private final String fingerprint;
        private int attempts;
        private BillerPaymentReceipt receipt;

        private CollectionState(String fingerprint) {
            this.fingerprint = fingerprint;
        }

        private synchronized BillerPaymentReceipt collect(
                BillerPaymentCommand command,
                int failuresBeforeSuccess,
                String rejectedReferencePrefix) {
            attempts++;
            if (attempts <= failuresBeforeSuccess) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Mock biller provider is unavailable.");
            }
            if (receipt == null) {
                String providerReference =
                        "MOCK-" + fingerprint.substring(0, 16).toUpperCase(Locale.ROOT);
                boolean rejected =
                        !rejectedReferencePrefix.isEmpty()
                                && command.customerReference().startsWith(rejectedReferencePrefix);
                receipt =
                        rejected
                                ? new BillerPaymentReceipt(
                                        providerReference,
                                        "REJECTED",
                                        "Mock biller rejected the customer reference.")
                                : new BillerPaymentReceipt(
                                        providerReference, "ACCEPTED", null);
                log.info(
                        "Mock biller payment {} for biller {}",
                        receipt.status().toLowerCase(Locale.ROOT),
                        command.billerCode());
            }
            return receipt;
        }
    }
}
