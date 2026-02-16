package com.baykanat.insider.assessment.domain.service;

import com.baykanat.insider.assessment.api.dto.MetricsQueryParams;
import com.baykanat.insider.assessment.api.dto.MetricsResponse;
import com.baykanat.insider.assessment.infrastructure.persistence.MetricsJdbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/** event_metrics materialized view'dan toplam ve zaman dilimi özetlerini sorgular, MetricsResponse oluşturur. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {

    private final MetricsJdbcRepository metricsRepository;

    /** Toplamlar + zaman dilimi breakdown'ları için MATERIALIZED VIEW'e iki sorgu atar, yanıtı birleştirir. */
    public MetricsResponse getMetrics(MetricsQueryParams params) {
        log.debug("Querying metrics for event_name={}, from={}, to={}, channel={}, groupBy={}",
                params.getEventName(), params.getFrom(), params.getTo(),
                params.getChannel(), params.getGroupBy());

        // Toplamlar
        long[] totals = metricsRepository.queryTotals(
                params.getEventName(), params.getFrom(), params.getTo(), params.getChannel());

        // Zaman dilimi özetleri
        List<MetricsResponse.TimeBucket> breakdowns = metricsRepository.queryBreakdowns(
                params.getEventName(), params.getFrom(), params.getTo(),
                params.getChannel(), params.getGroupBy());

        return MetricsResponse.builder()
                .eventName(params.getEventName())
                .totalCount(totals[0])
                .uniqueUserCount(totals[1])
                .timeRange(MetricsResponse.TimeRange.builder()
                        .from(params.getFrom())
                        .to(params.getTo())
                        .build())
                .channel(params.getChannel())
                .breakdowns(breakdowns)
                .build();
    }
}
