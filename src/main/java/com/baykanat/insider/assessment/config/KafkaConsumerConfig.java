package com.baykanat.insider.assessment.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/** Consumer hata işleme: exponential backoff retry, ardından DLT'ye gönderim. */
@Configuration
public class KafkaConsumerConfig {

    @Value("${app.kafka.topic.events-ingestion}")
    private String eventsIngestionTopic;

    /** Exponential backoff + DLT recoverer bean. */
    @SuppressWarnings("null")
    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaOperations<?, ?> kafkaOperations) {
        // Başarısız mesajlar → topic.DLT
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaOperations,
                (record, ex) -> new TopicPartition(eventsIngestionTopic + ".DLT", record.partition())
        );

        // Backoff: toplam 4 sn retry, sonra DLT (partition blocking kısa kalsın)
        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxInterval(4000L);
        backOff.setMaxElapsedTime(4000L);

        return new DefaultErrorHandler(recoverer, backOff);
    }
}
