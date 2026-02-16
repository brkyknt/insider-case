package com.baykanat.insider.assessment.scheduler;

import com.baykanat.insider.assessment.config.AppProperties;
import com.baykanat.insider.assessment.infrastructure.persistence.InboxJdbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Inbox tablosundan retention süresini aşan kayıtları periyodik siler (varsayılan 7 gün). */
@Slf4j
@Component
@RequiredArgsConstructor
public class InboxCleanupScheduler {

    private final InboxJdbcRepository inboxRepository;
    private final AppProperties appProperties;

    /** Yapılandırılmış retention gününden eski inbox kayıtlarını siler (varsayılan saatte bir). */
    @Scheduled(
            fixedRateString = "${app.scheduler.inbox-cleanup-rate:3600000}",
            initialDelayString = "60000"
    )
    public void cleanupOldInboxEntries() {
        try {
            int retentionDays = appProperties.getScheduler().getInboxRetentionDays();
            int deleted = inboxRepository.deleteOlderThan(retentionDays);
            if (deleted > 0) {
                log.info("Inbox cleanup: deleted {} entries older than {} days", deleted, retentionDays);
            } else {
                log.debug("Inbox cleanup: no entries older than {} days to delete", retentionDays);
            }
        } catch (Exception e) {
            log.error("Failed to cleanup inbox entries: {}", e.getMessage(), e);
        }
    }
}
