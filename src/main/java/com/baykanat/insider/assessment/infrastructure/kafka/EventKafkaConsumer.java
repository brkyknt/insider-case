package com.baykanat.insider.assessment.infrastructure.kafka;

import com.baykanat.insider.assessment.api.dto.EventRequest;
import com.baykanat.insider.assessment.domain.mapper.EventMapper;
import com.baykanat.insider.assessment.domain.service.EventIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.apache.kafka.common.header.Headers;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** events-ingestion topic'ten batch tüketir; EventIngestionService ile işler. Deserialize hataları DLT'ye. */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventKafkaConsumer {

    /** ErrorHandlingDeserializer hata durumunda bu header'ı set eder; value null olur. */
    private static final String VALUE_DESERIALIZATION_EXCEPTION_HEADER =
            "springDeserializationValueException";

    private final EventIngestionService eventIngestionService;
    private final EventMapper eventMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topic.events-ingestion}")
    private String eventsIngestionTopic;

    /** Batch alır; deserialize hataları DLT'ye, geçerli event'ler EventIngestionService'e. İşlem sonrası manuel ack. */
    @KafkaListener(
            topics = "${app.kafka.topic.events-ingestion}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(List<ConsumerRecord<String, Object>> records, Acknowledgment acknowledgment) {
        log.debug("Received batch of {} records from events-ingestion topic", records.size());

        List<EventRequest> events = new ArrayList<>(records.size());
        int dltCount = 0;

        for (ConsumerRecord<String, Object> record : records) {
            // Kafka deserialize hatası → value null, hata header'da
            if (hasDeserializationError(record)) {
                log.error("Kafka deserialization failed for record at offset={}, partition={}",
                        record.offset(), record.partition());
                publishToDlt(record, "Kafka-level deserialization failure");
                dltCount++;
                continue;
            }

            try {
                EventRequest event = deserialize(record);
                if (event != null) {
                    events.add(event);
                }
            } catch (Exception e) {
                // Uygulama deserialize hatası → DLT
                log.error("Failed to deserialize record at offset={}, partition={}: {}",
                        record.offset(), record.partition(), e.getMessage());
                publishToDlt(record, e.getMessage());
                dltCount++;
            }
        }

        if (!events.isEmpty()) {
            int inserted = eventIngestionService.processBatch(events);
            log.info("Batch processed: {} records received, {} events deserialized, {} new events inserted, {} sent to DLT",
                    records.size(), events.size(), inserted, dltCount);
        } else if (dltCount > 0) {
            log.warn("All {} records in batch failed deserialization, {} sent to DLT", records.size(), dltCount);
        }

        // İşlem sonrası ack (DLT'ye gidenler dahil)
        acknowledgment.acknowledge();
    }

    /** ErrorHandlingDeserializer hata header'ı var mı kontrol eder. */
    private boolean hasDeserializationError(ConsumerRecord<String, Object> record) {
        Headers headers = record.headers();
        return headers.lastHeader(VALUE_DESERIALIZATION_EXCEPTION_HEADER) != null;
    }

    /** Record value → EventRequest; EventMapper.fromRecordValue kullanır. */
    private EventRequest deserialize(ConsumerRecord<String, Object> record) {
        return eventMapper.fromRecordValue(record.value());
    }

    /** Başarısız kaydı DLT topic'ine gönderir. DLT gönderimi hata verirse sadece log, batch devam eder. */
    private void publishToDlt(ConsumerRecord<String, Object> record, String reason) {
        String dltTopic = Objects.requireNonNull(eventsIngestionTopic, "eventsIngestionTopic") + ".DLT";
        String topic = Objects.requireNonNull(dltTopic);
        try {
            String key = Objects.requireNonNullElse(record.key(), "");
            kafkaTemplate.send(Objects.requireNonNull(topic), Objects.requireNonNull(key, "key"), record.value());
            log.warn("Sent failed record to DLT: topic={}, offset={}, partition={}, reason={}",
                    topic, record.offset(), record.partition(), Objects.requireNonNullElse(reason, ""));
        } catch (Exception dltEx) {
            log.error("Failed to publish record to DLT {}: {}", topic, dltEx.getMessage());
        }
    }
}
