package com.baykanat.insider.assessment.api.controller;

import com.baykanat.insider.assessment.api.dto.MetricsQueryParams;
import com.baykanat.insider.assessment.api.dto.MetricsResponse;
import com.baykanat.insider.assessment.domain.service.MetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET /metrics — event_metrics materialized view'dan toplam/benzersiz sayı ve zaman dilimi özeti döner. */
@Slf4j
@RestController
@RequestMapping("/metrics")
@RequiredArgsConstructor
@Validated
@Tag(name = "Metrics", description = "Aggregated event metrics endpoint")
public class MetricsController {

    private final MetricsService metricsService;

    /** event_name, from, to zorunlu; channel ve group_by (hourly/daily) isteğe bağlı. */
    @GetMapping
    @Operation(summary = "Get aggregated metrics", description = "Returns event counts and unique user counts with time-bucketed breakdowns")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Metrics retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Missing or invalid query parameters")
    })
    public ResponseEntity<MetricsResponse> getMetrics(
            @Parameter(description = "Event name to filter by (mandatory)", required = true, example = "product_view")
            @RequestParam("event_name") @NotBlank String eventName,

            @Parameter(description = "Start of time range (Unix epoch seconds)", required = true, example = "1771113600")
            @RequestParam("from") @NotNull Long from,

            @Parameter(description = "End of time range (Unix epoch seconds)", required = true, example = "1771200000")
            @RequestParam("to") @NotNull Long to,

            @Parameter(description = "Optional channel filter", example = "web")
            @RequestParam(value = "channel", required = false) String channel,

            @Parameter(description = "Aggregation grouping: 'hourly' or 'daily'", example = "hourly")
            @RequestParam(value = "group_by", required = false, defaultValue = "hourly") String groupBy
    ) {
        MetricsQueryParams params = MetricsQueryParams.builder()
                .eventName(eventName)
                .from(from)
                .to(to)
                .channel(channel)
                .groupBy(groupBy)
                .build();

        MetricsResponse response = metricsService.getMetrics(params);
        return ResponseEntity.ok(response);
    }
}
