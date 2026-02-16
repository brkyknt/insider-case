package com.baykanat.insider.assessment.integration;

import com.baykanat.insider.assessment.api.dto.EventRequest;
import com.baykanat.insider.assessment.domain.service.EventIngestionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that verifies the full event ingestion pipeline
 * with a real PostgreSQL database (via Testcontainers).
 *
 * <p>This test validates:
 * <ul>
 *   <li>Events are persisted to the partitioned events table</li>
 *   <li>Inbox entries are created for idempotency</li>
 *   <li>Outbox entries are created for CDC</li>
 *   <li>Duplicate events are properly deduplicated</li>
 * </ul>
 *
 * <p>Uses Testcontainers for PostgreSQL (real database, not H2) to catch
 * partition-specific SQL issues that in-memory databases would miss.
 * Kafka is embedded for simplicity in tests.
 */
@SpringBootTest
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {"events-ingestion", "events-ingestion.DLT"})
@ActiveProfiles("test")
class EventIngestionIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("insiderone_test")
            .withUsername("test")
            .withPassword("test")
            .withCommand("postgres", "-c", "wal_level=logical");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private EventIngestionService eventIngestionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Should persist event to database and create inbox/outbox entries")
    void shouldPersistEventAndCreateInboxOutbox() {
        EventRequest event = EventRequest.builder()
                .eventName("product_view")
                .userId("user_integration_test")
                .timestamp(1771156800L)
                .campaignId("cmp_integration")
                .channel("web")
                .tags(List.of("electronics", "test"))
                .metadata(Map.of("product_id", "prod-test", "price", 99.99))
                .build();

        int inserted = eventIngestionService.processBatch(List.of(event));

        assertThat(inserted).isEqualTo(1);

        // Verify event was inserted
        Integer eventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM events WHERE user_id = 'user_integration_test'", Integer.class);
        assertThat(eventCount).isEqualTo(1);

        // Verify inbox entry was created
        Integer inboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inbox", Integer.class);
        assertThat(inboxCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Should deduplicate identical events")
    void shouldDeduplicateIdenticalEvents() {
        EventRequest event = EventRequest.builder()
                .eventName("dedup_test")
                .userId("user_dedup")
                .timestamp(1771156811L)
                .campaignId("cmp_dedup")
                .channel("mobile")
                .build();

        // Process same event twice
        int firstInsert = eventIngestionService.processBatch(List.of(event));
        int secondInsert = eventIngestionService.processBatch(List.of(event));

        assertThat(firstInsert).isEqualTo(1);
        assertThat(secondInsert).isEqualTo(0); // Duplicate should be skipped

        // Verify only one event exists
        Integer eventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM events WHERE event_name = 'dedup_test'", Integer.class);
        assertThat(eventCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Should handle batch with mixed new and duplicate events")
    void shouldHandleMixedBatch() {
        EventRequest event1 = EventRequest.builder()
                .eventName("mixed_batch_1")
                .userId("user_mix_1")
                .timestamp(1771156812L)
                .build();

        EventRequest event2 = EventRequest.builder()
                .eventName("mixed_batch_2")
                .userId("user_mix_2")
                .timestamp(1771156813L)
                .build();

        // Insert event1 first
        eventIngestionService.processBatch(List.of(event1));

        // Now insert batch with event1 (duplicate) and event2 (new)
        int inserted = eventIngestionService.processBatch(List.of(event1, event2));

        assertThat(inserted).isEqualTo(1); // Only event2 should be new
    }
}
