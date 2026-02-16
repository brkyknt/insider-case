package com.baykanat.insider.assessment.scheduler;

import com.baykanat.insider.assessment.infrastructure.persistence.MetricsJdbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** event_metrics materialized view'ı periyodik CONCURRENTLY yeniler (varsayılan 1 dk). */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaterializedViewRefreshScheduler {

    private final MetricsJdbcRepository metricsRepository;

    /** MATERIALIZED VIEW'i yapılandırılmış aralıkta yeniler (parametrik: application.yaml); hata olursa sadece log. */
    @Scheduled(
            fixedRateString = "${app.scheduler.materialized-view-refresh-rate:60000}",
            initialDelayString = "${app.scheduler.materialized-view-refresh-initial-delay:30000}"
    )
    public void refreshMaterializedView() {
        try {
            metricsRepository.refreshMaterializedView();
        } catch (Exception e) {
            log.error("Failed to refresh materialized view: {}", e.getMessage(), e);
        }
    }
}
