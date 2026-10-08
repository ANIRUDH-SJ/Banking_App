package com.netbanking.events;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression(
        "'${app.events.transport:http}' == 'kafka' and '${app.events.kafka.consumer-topic:}' != ''")
public class KafkaEventListener {
    private final BankingEventHandler handler;
    private final Validator validator;
    private final String acceptedType;

    public KafkaEventListener(
            BankingEventHandler handler,
            Validator validator,
            @Value("${app.events.kafka.consumer-type}") String acceptedType) {
        this.handler = handler;
        this.validator = validator;
        this.acceptedType = acceptedType;
    }

    @KafkaListener(
            topics = "${app.events.kafka.consumer-topic}",
            groupId = "${spring.application.name}")
    public void receive(EventEnvelope event) {
        var violations = validator.validate(event);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
        if (!acceptedType.equals(event.type())) return;
        handler.accept(event);
    }
}
