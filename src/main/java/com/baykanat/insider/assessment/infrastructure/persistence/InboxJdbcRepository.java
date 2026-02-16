package com.baykanat.insider.assessment.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Inbox (idempotency) tablosu: toplu key kontrolü ve toplu insert. */
@Slf4j
@Repository
@RequiredArgsConstructor
public class InboxJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /** Verilen key'lerden inbox'ta olanları döner. */
    public Set<String> findExistingKeys(Collection<String> keys) {
        if (keys.isEmpty()) {
            return Set.of();
        }

        String placeholders = String.join(",", keys.stream().map(k -> "?").toList());
        String sql = "SELECT idempotency_key FROM inbox WHERE idempotency_key IN (" + placeholders + ")";

        return new HashSet<>(jdbcTemplate.queryForList(sql, String.class, keys.toArray()));
    }

    /** Idempotency key'leri toplu insert; ON CONFLICT DO NOTHING. */
    public void batchInsert(Collection<String> keys) {
        if (keys.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO inbox (idempotency_key) VALUES (?) ON CONFLICT DO NOTHING";
        List<Object[]> batchArgs = keys.stream()
                .map(key -> new Object[]{Objects.requireNonNull(key, "key")})
                .toList();
        jdbcTemplate.batchUpdate(sql, Objects.requireNonNull(batchArgs));
    }

    /** Tek key inbox'ta var mı. */
    public boolean exists(String key) {
        String sql = "SELECT COUNT(*) FROM inbox WHERE idempotency_key = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, key);
        return count != null && count > 0;
    }

    /** Belirtilen günden eski inbox kayıtlarını siler; silinen sayıyı döner. */
    public int deleteOlderThan(int retentionDays) {
        String sql = "DELETE FROM inbox WHERE received_at < NOW() - INTERVAL '1 day' * ?";
        return jdbcTemplate.update(sql, retentionDays);
    }
}
