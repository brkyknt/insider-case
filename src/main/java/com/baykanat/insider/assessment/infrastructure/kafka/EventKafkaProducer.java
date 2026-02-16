package com.baykanat.insider.assessment.infrastructure.kafka;

import com.baykanat.insider.assessment.api.dto.EventRequest;
import com.baykanat.insider.assessment.config.AppProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Event'leri Kafka'ya gönderir; Retry + Circuit Breaker. Tek event senkron, toplu gönderim paralel. */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventKafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AppProperties appProperties;

    /** Tek event gönderir; partition key user_id. Ack beklenir (acks=all). */
    @Retry(name = "kafkaProducer")
    @CircuitBreaker(name = "kafkaProducer", fallbackMethod = "handleCircuitBreakerOpen")
    public void send(EventRequest event) throws Exception {
        String topic = Objects.requireNonNull(appProperties.getKafka().getTopic().getEventsIngestion());
        String key = Objects.requireNonNull(event.getUserId(), "userId");
        kafkaTemplate.send(topic, key, event).get(1, TimeUnit.SECONDS);
    }

    /** Toplu event'leri paralel gönderir; tüm ack'ler paralel beklenir. */
    @CircuitBreaker(name = "kafkaProducer", fallbackMethod = "handleBatchCircuitBreakerOpen")
    public void sendBatch(List<EventRequest> events) throws Exception {
        String topic = Objects.requireNonNull(appProperties.getKafka().getTopic().getEventsIngestion());

        List<CompletableFuture<SendResult<String, Object>>> futures = events.stream()
                .map(event -> kafkaTemplate.send(topic, Objects.requireNonNull(event.getUserId(), "userId"), event))
                .toList();

        // Tüm ack'leri paralel bekle
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(10, TimeUnit.SECONDS);
    }

    /** Circuit breaker açıkken tek event için 503 + Retry-After. */
    @SuppressWarnings("unused")
    private void handleCircuitBreakerOpen(EventRequest event, CallNotPermittedException ex) {
        log.error("Circuit breaker is OPEN for Kafka producer. Rejecting event for user_id={}",
                event.getUserId());
        throw new ServiceUnavailableException(
                "Event ingestion is temporarily unavailable. Kafka circuit breaker is open.", 30);
    }

    /** Tek event için tüm retry'lar tükendikten sonra fallback. */
    @SuppressWarnings("unused")
    private void handleCircuitBreakerOpen(EventRequest event, Exception ex) {
        log.error("Kafka produce failed after all retries for user_id={}: {}",
                event.getUserId(), ex.getMessage());
        throw new ServiceUnavailableException(
                "Event ingestion is temporarily unavailable. " + ex.getMessage(), 30);
    }

    /** Toplu gönderimde circuit breaker açıkken fallback. */
    @SuppressWarnings("unused")
    private void handleBatchCircuitBreakerOpen(List<EventRequest> events, CallNotPermittedException ex) {
        log.error("Circuit breaker is OPEN for Kafka producer. Rejecting batch of {} events", events.size());
        throw new ServiceUnavailableException(
                "Event ingestion is temporarily unavailable. Kafka circuit breaker is open.", 30);
    }

    /** Toplu gönderim hatasında fallback. */
    @SuppressWarnings("unused")
    private void handleBatchCircuitBreakerOpen(List<EventRequest> events, Exception ex) {
        log.error("Kafka batch produce failed for {} events: {}", events.size(), ex.getMessage());
        throw new ServiceUnavailableException(
                "Event ingestion is temporarily unavailable. " + ex.getMessage(), 30);
    }

    /** Circuit breaker açık veya Kafka yok; GlobalExceptionHandler 503 + Retry-After döner. */
    public static class ServiceUnavailableException extends RuntimeException {
        private final int retryAfterSeconds;

        public ServiceUnavailableException(String message, int retryAfterSeconds) {
            super(message);
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public int getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}
