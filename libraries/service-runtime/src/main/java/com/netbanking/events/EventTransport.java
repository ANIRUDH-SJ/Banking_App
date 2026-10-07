package com.netbanking.events;

/** Delivers a serialized outbox envelope without changing its durable database record. */
public interface EventTransport {
    void send(String destination, String envelope) throws Exception;
}
