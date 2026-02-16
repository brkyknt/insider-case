package com.baykanat.insider.assessment.api.controller;

import com.baykanat.insider.assessment.infrastructure.kafka.EventKafkaProducer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer tests for EventController.
 *
 * <p>Uses @WebMvcTest to test only the controller layer with mocked dependencies.
 * Kafka producer is mocked — these tests verify:
 * <ul>
 *   <li>Request validation (400 on invalid payloads)</li>
 *   <li>Happy path response (202 Accepted)</li>
 *   <li>Response body structure</li>
 * </ul>
 *
 * <p>Integration tests with real Kafka and PostgreSQL are handled separately
 * via Testcontainers.
 */
@WebMvcTest(EventController.class)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EventKafkaProducer kafkaProducer;

    @Test
    @DisplayName("POST /events - valid event should return 202 Accepted")
    void validEventReturns202() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "event_name", "product_view",
                "user_id", "user_123",
                "timestamp", 1771156800,
                "channel", "web",
                "campaign_id", "cmp_987",
                "tags", List.of("electronics", "homepage"),
                "metadata", Map.of("product_id", "prod-789", "price", 129.99)
        ));

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.acceptedCount").value(1))
                .andExpect(jsonPath("$.message").value("Event queued for processing"));
    }

    @Test
    @DisplayName("POST /events - missing event_name should return 400")
    void missingEventNameReturns400() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "user_id", "user_123",
                "timestamp", 1771156800
        ));

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    @DisplayName("POST /events - missing user_id should return 400")
    void missingUserIdReturns400() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "event_name", "product_view",
                "timestamp", 1771156800
        ));

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /events - missing timestamp should return 400")
    void missingTimestampReturns400() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "event_name", "product_view",
                "user_id", "user_123"
        ));

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /events - negative timestamp should return 400")
    void negativeTimestampReturns400() throws Exception {
        String payload = """
                {
                    "event_name": "product_view",
                    "user_id": "user_123",
                    "timestamp": -1
                }
                """;

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }


    @Test
    @DisplayName("POST /events/bulk - valid bulk request should return 202")
    void validBulkRequestReturns202() throws Exception {
        String payload = """
                {
                    "events": [
                        {
                            "event_name": "product_view",
                            "user_id": "user_123",
                            "timestamp": 1771156800,
                            "channel": "web"
                        },
                        {
                            "event_name": "add_to_cart",
                            "user_id": "user_456",
                            "timestamp": 1771156810,
                            "channel": "mobile"
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/events/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.acceptedCount").value(2));
    }

    @Test
    @DisplayName("POST /events/bulk - empty events list should return 400")
    void emptyBulkRequestReturns400() throws Exception {
        String payload = """
                {
                    "events": []
                }
                """;

        mockMvc.perform(post("/events/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /events/bulk - invalid event in bulk should return 400")
    void invalidEventInBulkReturns400() throws Exception {
        String payload = """
                {
                    "events": [
                        {
                            "event_name": "product_view",
                            "user_id": "user_123",
                            "timestamp": 1771156800
                        },
                        {
                            "user_id": "user_456",
                            "timestamp": 1771156810
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/events/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /events - empty JSON object should return 400")
    void emptyJsonObjectReturns400() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
