package com.netbanking.events;

/** Service-owned event handler shared by HTTP fallback and Kafka delivery. */
public interface BankingEventHandler {
    void accept(EventEnvelope event);
}
