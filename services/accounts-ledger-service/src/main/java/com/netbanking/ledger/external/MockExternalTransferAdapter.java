package com.netbanking.ledger.external;

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
        name = "app.external-transfer.provider",
        havingValue = "mock",
        matchIfMissing = true)
public class MockExternalTransferAdapter implements ExternalTransferAdapter {
    private static final Logger log = LoggerFactory.getLogger(MockExternalTransferAdapter.class);

    private final ConcurrentMap<String, DispatchState> dispatches = new ConcurrentHashMap<>();
    private final int failuresBeforeSuccess;

    public MockExternalTransferAdapter(
            @Value("${app.external-transfer.mock.failures-before-success:0}")
                    int failuresBeforeSuccess) {
        if (failuresBeforeSuccess < 0) {
            throw new IllegalArgumentException(
                    "Mock external transfer failures must not be negative.");
        }
        this.failuresBeforeSuccess = failuresBeforeSuccess;
    }

    @Override
    public ExternalTransferReceipt transfer(ExternalTransferCommand command) {
        String fingerprint =
                RequestFingerprint.of(
                        command.destinationAccountNumber(),
                        command.destinationIfsc(),
                        command.amount(),
                        command.currencyCode(),
                        command.narration());
        DispatchState state =
                dispatches.compute(
                        command.operationId(),
                        (key, current) -> {
                            if (current != null && !current.fingerprint.equals(fingerprint)) {
                                throw new ConflictException(
                                        "External transfer key was used for different details.");
                            }
                            return current == null ? new DispatchState(fingerprint) : current;
                        });
        return state.dispatch(command, failuresBeforeSuccess);
    }

    private static String lastFour(String accountNumber) {
        return accountNumber.length() <= 4
                ? accountNumber
                : accountNumber.substring(accountNumber.length() - 4);
    }

    private static final class DispatchState {
        private final String fingerprint;
        private int attempts;
        private ExternalTransferReceipt receipt;

        private DispatchState(String fingerprint) {
            this.fingerprint = fingerprint;
        }

        private synchronized ExternalTransferReceipt dispatch(
                ExternalTransferCommand command, int failuresBeforeSuccess) {
            attempts++;
            if (attempts <= failuresBeforeSuccess) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Mock external transfer provider is unavailable.");
            }
            if (receipt == null) {
                receipt =
                        new ExternalTransferReceipt(
                                "MOCK-"
                                        + fingerprint
                                                .substring(0, 16)
                                                .toUpperCase(Locale.ROOT),
                                "ACCEPTED");
                log.info(
                        "Mock external transfer accepted for account ending {}",
                        lastFour(command.destinationAccountNumber()));
            }
            return receipt;
        }
    }
}
