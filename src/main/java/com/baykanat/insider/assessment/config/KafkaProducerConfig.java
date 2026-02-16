package com.baykanat.insider.assessment.config;

import org.apache.kafka.clients.admin.NewTopic;

import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Ingestion ve DLT topic bean'leri; partition/replica sayıları burada. */
@Configuration
public class KafkaProducerConfig {

    @Value("${app.kafka.topic.events-ingestion}")
    private String eventsIngestionTopic;

    /** Ana ingestion topic. 6 partition: 3 consumer thread x 2 partition (application.yaml), ~30K/sn kapasite; ileride 6'ya kadar consumer ölçeklenebilir. */
    @Bean
    public NewTopic eventsIngestionTopic() {
        return TopicBuilder.name(Objects.requireNonNull(eventsIngestionTopic, "eventsIngestionTopic"))
                .partitions(6)
                .replicas(1)
                .build();
    }

    /** Retry sonrası başarısız mesajlar için DLT topic. */
    @Bean
    public NewTopic eventsIngestionDlt() {
        return TopicBuilder.name(Objects.requireNonNull(eventsIngestionTopic, "eventsIngestionTopic") + ".DLT")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
