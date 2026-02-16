package com.baykanat.insider.assessment.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Inbox (idempotency) tablosu kaydı; işlenen event'lerin key'i burada tutulur. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboxEntry {

    private String idempotencyKey;
    private Instant receivedAt;
}
