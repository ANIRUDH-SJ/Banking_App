package com.netbanking.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netbanking.discovery.ServiceHttpClient;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.events.transport",
        havingValue = "http",
        matchIfMissing = true)
public class HttpEventTransport implements EventTransport {
    private final ServiceHttpClient client;
    private final ObjectMapper json;

    public HttpEventTransport(ServiceHttpClient client, ObjectMapper json) {
        this.client = client;
        this.json = json;
    }

    @Override
    public void send(String destination, String envelope) throws Exception {
        client.post(
                destination,
                "/internal/events",
                json.readValue(envelope, EventEnvelope.class),
                Void.class);
    }
}
