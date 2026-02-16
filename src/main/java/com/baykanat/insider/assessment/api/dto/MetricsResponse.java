package com.baykanat.insider.assessment.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Metrik yanıtı: toplam sayı, benzersiz kullanıcı sayısı, zaman dilimi breakdown'ları. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Aggregated metrics response")
public class MetricsResponse {

    @JsonProperty("event_name")
    @Schema(description = "Filtered event name", example = "product_view")
    private String eventName;

    @JsonProperty("total_count")
    @Schema(description = "Total number of events matching the criteria", example = "15234")
    private long totalCount;

    @JsonProperty("unique_user_count")
    @Schema(description = "Number of distinct users", example = "8721")
    private long uniqueUserCount;

    @JsonProperty("time_range")
    @Schema(description = "Applied time range filter")
    private TimeRange timeRange;

    @JsonProperty("channel")
    @Schema(description = "Applied channel filter (if any)", example = "web")
    private String channel;

    @JsonProperty("breakdowns")
    @Schema(description = "Time-bucketed aggregation breakdown")
    private List<TimeBucket> breakdowns;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Time range boundaries")
    public static class TimeRange {
        @Schema(description = "Start timestamp (Unix epoch)", example = "1771113600")
        private long from;
        @Schema(description = "End timestamp (Unix epoch)", example = "1771200000")
        private long to;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Aggregation for a time bucket")
    public static class TimeBucket {
        @JsonProperty("bucket")
        @Schema(description = "Time bucket label", example = "2024-08-12T14:00:00Z")
        private String bucket;

        @JsonProperty("total_count")
        @Schema(description = "Event count in this bucket", example = "1523")
        private long totalCount;

        @JsonProperty("unique_user_count")
        @Schema(description = "Unique users in this bucket", example = "872")
        private long uniqueUserCount;
    }
}
