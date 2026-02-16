package com.baykanat.insider.assessment.domain.service;

import com.baykanat.insider.assessment.api.dto.EventRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for IdempotencyService.
 *
 * <p>These tests verify the core idempotency key generation logic without any
 * external dependencies (no Spring context, no database, no Kafka).
 * Fast, deterministic, and runnable in isolation.
 */
class IdempotencyServiceTest {

    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService();
    }

    @Test
    @DisplayName("Same event fields should produce the same idempotency key")
    void sameEventProducesSameKey() {
        EventRequest event1 = EventRequest.builder()
                .eventName("product_view")
                .userId("user_123")
                .timestamp(1771156800L)
                .campaignId("cmp_987")
                .build();

        EventRequest event2 = EventRequest.builder()
                .eventName("product_view")
                .userId("user_123")
                .timestamp(1771156800L)
                .campaignId("cmp_987")
                .build();

        String key1 = idempotencyService.generateKey(event1);
        String key2 = idempotencyService.generateKey(event2);

        assertThat(key1).isEqualTo(key2);
        assertThat(key1).hasSize(64); // SHA-256 produces 64 hex characters
    }

    @Test
    @DisplayName("Different event_name should produce different key")
    void differentEventNameProducesDifferentKey() {
        EventRequest event1 = EventRequest.builder()
                .eventName("product_view")
                .userId("user_123")
                .timestamp(1771156800L)
                .campaignId("cmp_987")
                .build();

        EventRequest event2 = EventRequest.builder()
                .eventName("add_to_cart")
                .userId("user_123")
                .timestamp(1771156800L)
                .campaignId("cmp_987")
                .build();

        assertThat(idempotencyService.generateKey(event1))
                .isNotEqualTo(idempotencyService.generateKey(event2));
    }

    @Test
    @DisplayName("Different user_id should produce different key")
    void differentUserIdProducesDifferentKey() {
        EventRequest event1 = EventRequest.builder()
                .eventName("product_view")
                .userId("user_123")
                .timestamp(1771156800L)
                .campaignId("cmp_987")
                .build();

        EventRequest event2 = EventRequest.builder()
                .eventName("product_view")
                .userId("user_456")
                .timestamp(1771156800L)
                .campaignId("cmp_987")
                .build();

        assertThat(idempotencyService.generateKey(event1))
                .isNotEqualTo(idempotencyService.generateKey(event2));
    }

    @Test
    @DisplayName("Different timestamp should produce different key")
    void differentTimestampProducesDifferentKey() {
        EventRequest event1 = EventRequest.builder()
                .eventName("product_view")
                .userId("user_123")
                .timestamp(1771156800L)
                .campaignId("cmp_987")
                .build();

        EventRequest event2 = EventRequest.builder()
                .eventName("product_view")
                .userId("user_123")
                .timestamp(1771156810L)
                .campaignId("cmp_987")
                .build();

        assertThat(idempotencyService.generateKey(event1))
                .isNotEqualTo(idempotencyService.generateKey(event2));
    }

    @Test
    @DisplayName("Different campaign_id should produce different key")
    void differentCampaignIdProducesDifferentKey() {
        EventRequest event1 = EventRequest.builder()
                .eventName("product_view")
                .userId("user_123")
                .timestamp(1771156800L)
                .campaignId("cmp_987")
                .build();

        EventRequest event2 = EventRequest.builder()
                .eventName("product_view")
                .userId("user_123")
                .timestamp(1771156800L)
                .campaignId("cmp_456")
                .build();

        assertThat(idempotencyService.generateKey(event1))
                .isNotEqualTo(idempotencyService.generateKey(event2));
    }

    @Test
    @DisplayName("Null campaign_id should be handled gracefully")
    void nullCampaignIdIsHandled() {
        EventRequest event = EventRequest.builder()
                .eventName("product_view")
                .userId("user_123")
                .timestamp(1771156800L)
                .campaignId(null)
                .build();

        String key = idempotencyService.generateKey(event);

        assertThat(key).isNotNull().hasSize(64);
    }

    @Test
    @DisplayName("Key generation is deterministic across multiple calls")
    void keyGenerationIsDeterministic() {
        EventRequest event = EventRequest.builder()
                .eventName("product_view")
                .userId("user_123")
                .timestamp(1771156800L)
                .campaignId("cmp_987")
                .build();

        String key1 = idempotencyService.generateKey(event);
        String key2 = idempotencyService.generateKey(event);
        String key3 = idempotencyService.generateKey(event);

        assertThat(key1).isEqualTo(key2).isEqualTo(key3);
    }
}
