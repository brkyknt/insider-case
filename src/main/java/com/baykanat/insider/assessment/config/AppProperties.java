package com.baykanat.insider.assessment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** app.* için tip güvenli configuration (Kafka topic adları, scheduler aralıkları, inbox retention). */
@Configuration
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {

    private KafkaTopicProperties kafka = new KafkaTopicProperties();
    private SchedulerProperties scheduler = new SchedulerProperties();

    @Getter
    @Setter
    public static class KafkaTopicProperties {
        private TopicNames topic = new TopicNames();

        @Getter
        @Setter
        public static class TopicNames {
            private String eventsIngestion = "events-ingestion";
        }
    }

    @Getter
    @Setter
    public static class SchedulerProperties {
        /** MV yenileme aralığı (ms). */
        private long materializedViewRefreshRate = 60000;
        /** MV ilk yenileme gecikmesi (ms); uygulama açılışından sonra. */
        private long materializedViewRefreshInitialDelay = 30000;
        private long inboxCleanupRate = 3600000;
        private int inboxRetentionDays = 7;
        /** event_metrics MV'de kullanılan son N gün (app_config ile senkron). */
        private int mvRetentionDays = 7;
    }
}
