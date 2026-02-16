package com.baykanat.insider.assessment.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Uygulama açılışında app_config tablosunu application.yaml değerleriyle senkronize eder. */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class AppConfigSyncRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final AppProperties appProperties;

    @Override
    public void run(ApplicationArguments args) {
        int days = appProperties.getScheduler().getMvRetentionDays();
        jdbcTemplate.update(
                "INSERT INTO app_config (key, value) VALUES ('mv_retention_days', ?) ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value",
                String.valueOf(days));
        log.debug("app_config: mv_retention_days={}", days);
    }
}
