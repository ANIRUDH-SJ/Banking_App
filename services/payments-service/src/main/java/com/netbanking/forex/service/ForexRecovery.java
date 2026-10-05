package com.netbanking.forex.service;

import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ForexRecovery {
    private final ForexStore store;
    private final ForexService service;

    public ForexRecovery(ForexStore store, ForexService service) {
        this.store = store;
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${app.forex.recovery-ms:15000}")
    public void recover() {
        for (var operation : store.recoverable()) {
            try {
                service.settle(operation);
            } catch (RuntimeException failure) {
                store.defer(operation.operationId());
                LoggerFactory.getLogger(getClass()).warn(
                        "Forex conversion {} remains under recovery", operation.operationId());
            }
        }
    }
}
