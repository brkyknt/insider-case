package com.baykanat.insider.assessment.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** events tablosu satırı için domain model (JDBC, JPA değil). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Event {

    private Long id;
    private String eventName;
    private String channel;
    private String campaignId;
    private String userId;
    private long eventTimestamp;
    private LocalDate eventDate;
    private String tags;    // JSONB (String olarak)
    private String metadata; // JSONB (String olarak)
    private String idempotencyKey;
    private Instant createdAt;

    /** event_timestamp → event_date (partition sütunu). */
    public LocalDate deriveEventDate() {
        return Instant.ofEpochSecond(this.eventTimestamp)
                .atOffset(ZoneOffset.UTC)
                .toLocalDate();
    }
}
