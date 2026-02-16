package com.baykanat.insider.assessment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** GET /metrics sorgu parametreleri: event_name, from, to zorunlu; channel, group_by isteğe bağlı. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Query parameters for metrics endpoint")
public class MetricsQueryParams {

    @NotBlank(message = "event_name is required for metrics queries")
    @Schema(description = "Event name to filter by (mandatory)", example = "product_view")
    private String eventName;

    @NotNull(message = "from timestamp is required")
    @Schema(description = "Start of time range (Unix epoch seconds)", example = "1771113600")
    private Long from;

    @NotNull(message = "to timestamp is required")
    @Schema(description = "End of time range (Unix epoch seconds)", example = "1771200000")
    private Long to;

    @Schema(description = "Optional channel filter", example = "web")
    private String channel;

    @Schema(description = "Aggregation grouping: 'hourly' or 'daily'. Default: hourly", example = "hourly")
    private String groupBy;

    public String getGroupBy() {
        return groupBy == null || groupBy.isBlank() ? "hourly" : groupBy;
    }
}
