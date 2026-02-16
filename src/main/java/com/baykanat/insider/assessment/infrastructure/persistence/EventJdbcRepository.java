package com.baykanat.insider.assessment.infrastructure.persistence;

import com.baykanat.insider.assessment.domain.model.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Types;
import java.util.List;

/** events tablosuna JDBC batch insert. ON CONFLICT DO NOTHING ile idempotency. */
@Slf4j
@Repository
@RequiredArgsConstructor
public class EventJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final String INSERT_SQL = """
            INSERT INTO events (event_name, channel, campaign_id, user_id, event_timestamp, event_date, tags, metadata, idempotency_key)
            VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)
            ON CONFLICT (idempotency_key, event_date) DO NOTHING
            """;

    /** Event listesini batch insert eder; çakışmada satır atlanır. */
    public int[][] batchInsert(List<Event> events) {
        return jdbcTemplate.batchUpdate(INSERT_SQL, events, events.size(),
                (ps, event) -> {
                    ps.setString(1, event.getEventName());
                    ps.setString(2, event.getChannel());
                    ps.setString(3, event.getCampaignId());
                    ps.setString(4, event.getUserId());
                    ps.setLong(5, event.getEventTimestamp());
                    ps.setDate(6, Date.valueOf(event.getEventDate()));
                    if (event.getTags() != null) {
                        ps.setString(7, event.getTags());
                    } else {
                        ps.setNull(7, Types.OTHER);
                    }
                    if (event.getMetadata() != null) {
                        ps.setString(8, event.getMetadata());
                    } else {
                        ps.setNull(8, Types.OTHER);
                    }
                    ps.setString(9, event.getIdempotencyKey());
                });
    }

    /** Tek event insert; batchInsert'e yönlendirir. */
    public void insert(Event event) {
        batchInsert(List.of(event));
    }
}
