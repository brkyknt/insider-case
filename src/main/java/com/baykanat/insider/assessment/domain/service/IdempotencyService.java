package com.baykanat.insider.assessment.domain.service;

import com.baykanat.insider.assessment.api.dto.EventRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** event_name + user_id + timestamp + campaign_id için SHA-256 idempotency key üretir. */
@Slf4j
@Service
public class IdempotencyService {

    private static final String SEPARATOR = "|";

    /** Event için 64 karakterlik hex idempotency key döner. */
    public String generateKey(EventRequest event) {
        String raw = event.getEventName() + SEPARATOR
                + event.getUserId() + SEPARATOR
                + event.getTimestamp() + SEPARATOR
                + (event.getCampaignId() != null ? event.getCampaignId() : "");

        return sha256(raw);
    }

    /** Girdi string'in SHA-256 hash'ini hesaplar. */
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
