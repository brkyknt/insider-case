package com.baykanat.insider.assessment.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/** Gelen event payload DTO; API katmanında doğrulama (Kafka'ya göndermeden önce). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Event payload for ingestion")
public class EventRequest {

    @NotBlank(message = "event_name is required")
    @JsonProperty("event_name")
    @Schema(description = "Name of the event", example = "product_view")
    private String eventName;

    @JsonProperty("channel")
    @Schema(description = "Channel where the event originated", example = "web")
    private String channel;

    @JsonProperty("campaign_id")
    @Schema(description = "Associated campaign identifier", example = "cmp_987")
    private String campaignId;

    @NotBlank(message = "user_id is required")
    @JsonProperty("user_id")
    @Schema(description = "Unique user identifier", example = "user_123")
    private String userId;

    @NotNull(message = "timestamp is required")
    @Positive(message = "timestamp must be a positive Unix epoch value")
    @JsonProperty("timestamp")
    @Schema(description = "Event timestamp as Unix epoch seconds", example = "1771156800")
    private Long timestamp;

    @JsonProperty("tags")
    @Schema(description = "Event tags", example = "[\"electronics\", \"homepage\", \"flash_sale\"]")
    private List<String> tags;

    @JsonProperty("metadata")
    @Schema(description = "Additional event metadata", example = "{\"product_id\": \"prod-789\", \"price\": 129.99}")
    private Map<String, Object> metadata;
}
