package com.netbanking.events;

import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaAdmin.NewTopics;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnProperty(name = "app.events.transport", havingValue = "kafka")
public class KafkaEventConfiguration {
    @Bean
    SmartInitializingSingleton kafkaTopicsInitializer(KafkaAdmin admin) {
        admin.setFatalIfBrokerNotAvailable(true);
        return () -> {
            if (!admin.initialize())
                throw new IllegalStateException("Kafka topics could not be initialized.");
        };
    }

    @Bean
    NewTopics bankingEventTopics(
            EventTopics topics,
            @Value("${app.events.kafka.partitions:3}") int partitions,
            @Value("${app.events.kafka.replication-factor:1}") short replicas) {
        return new NewTopics(
                TopicBuilder.name(topics.customer()).partitions(partitions).replicas(replicas).build(),
                TopicBuilder.name(topics.notifications())
                        .partitions(partitions)
                        .replicas(replicas)
                        .build(),
                TopicBuilder.name(topics.audit()).partitions(partitions).replicas(replicas).build(),
                TopicBuilder.name(topics.customer() + ".dlt")
                        .partitions(partitions)
                        .replicas(replicas)
                        .build(),
                TopicBuilder.name(topics.notifications() + ".dlt")
                        .partitions(partitions)
                        .replicas(replicas)
                        .build(),
                TopicBuilder.name(topics.audit() + ".dlt")
                        .partitions(partitions)
                        .replicas(replicas)
                        .build());
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<Object, Object> template,
            @Value("${app.events.kafka.retry-interval-ms:1000}") long retryInterval,
            @Value("${app.events.kafka.retry-attempts:3}") long retryAttempts) {
        var recoverer =
                new DeadLetterPublishingRecoverer(
                        template,
                        (record, failure) ->
                                new TopicPartition(record.topic() + ".dlt", record.partition()));
        return new DefaultErrorHandler(
                recoverer, new FixedBackOff(retryInterval, Math.max(0, retryAttempts - 1)));
    }
}
