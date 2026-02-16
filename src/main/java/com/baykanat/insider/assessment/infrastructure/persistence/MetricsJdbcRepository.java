package com.baykanat.insider.assessment.infrastructure.persistence;

import com.baykanat.insider.assessment.api.dto.MetricsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** event_metrics materialized view üzerinden toplam ve zaman dilimi sorguları; REFRESH CONCURRENTLY. */
@Slf4j
@Repository
@RequiredArgsConstructor
public class MetricsJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /** MV'den toplam sayı ve benzersiz kullanıcı sayısı; [totalCount, uniqueUserCount]. */
    public long[] queryTotals(String eventName, long from, long to, String channel) {
        StringBuilder sql = new StringBuilder("""
                SELECT COALESCE(SUM(total_count), 0) AS total_count,
                       COALESCE(SUM(unique_user_count), 0) AS unique_user_count
                FROM event_metrics
                WHERE event_name = ?
                  AND date_hour >= ?
                  AND date_hour < ?
                """);

        List<Object> params = new ArrayList<>();
        params.add(eventName);
        params.add(Timestamp.from(Instant.ofEpochSecond(from)));
        params.add(Timestamp.from(Instant.ofEpochSecond(to)));

        if (channel != null && !channel.isBlank()) {
            sql.append(" AND channel = ?");
            params.add(channel);
        }

        String sqlStr = Objects.requireNonNull(sql.toString());
        return jdbcTemplate.queryForObject(sqlStr, (rs, rowNum) -> new long[]{
                rs.getLong("total_count"),
                rs.getLong("unique_user_count")
        }, params.toArray());
    }

    /** Zaman dilimi özetleri; groupBy hourly veya daily. */
    public List<MetricsResponse.TimeBucket> queryBreakdowns(String eventName, long from, long to,
                                                             String channel, String groupBy) {
        String truncExpr = "daily".equalsIgnoreCase(groupBy)
                ? "DATE_TRUNC('day', date_hour)"
                : "date_hour";

        StringBuilder sql = new StringBuilder(String.format("""
                SELECT %s AS bucket,
                       SUM(total_count) AS total_count,
                       SUM(unique_user_count) AS unique_user_count
                FROM event_metrics
                WHERE event_name = ?
                  AND date_hour >= ?
                  AND date_hour < ?
                """, truncExpr));

        List<Object> params = new ArrayList<>();
        params.add(eventName);
        params.add(Timestamp.from(Instant.ofEpochSecond(from)));
        params.add(Timestamp.from(Instant.ofEpochSecond(to)));

        if (channel != null && !channel.isBlank()) {
            sql.append(" AND channel = ?");
            params.add(channel);
        }

        sql.append(String.format(" GROUP BY %s ORDER BY %s", truncExpr, truncExpr));

        String sqlStr = Objects.requireNonNull(sql.toString());
        return jdbcTemplate.query(sqlStr, (rs, rowNum) -> {
                Timestamp bucketTs = Objects.requireNonNull(rs.getTimestamp("bucket"), "bucket");
                String bucketStr = Objects.requireNonNull(bucketTs.toInstant().toString(), "bucket");
                return MetricsResponse.TimeBucket.builder()
                        .bucket(bucketStr)
                        .totalCount(rs.getLong("total_count"))
                        .uniqueUserCount(rs.getLong("unique_user_count"))
                        .build();
        }, params.toArray());
    }

    /** event_metrics MV'yi CONCURRENTLY yeniler. */
    public void refreshMaterializedView() {
        log.info("Refreshing event_metrics materialized view...");
        long start = System.currentTimeMillis();
        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY event_metrics");
        log.info("Materialized view refresh completed in {}ms", System.currentTimeMillis() - start);
    }
}
