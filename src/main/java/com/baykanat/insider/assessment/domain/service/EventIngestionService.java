package com.baykanat.insider.assessment.domain.service;

import com.baykanat.insider.assessment.api.dto.EventRequest;
import com.baykanat.insider.assessment.domain.mapper.EventMapper;
import com.baykanat.insider.assessment.domain.model.Event;
import com.baykanat.insider.assessment.infrastructure.persistence.EventJdbcRepository;
import com.baykanat.insider.assessment.infrastructure.persistence.InboxJdbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/** Kafka consumer tarafı: inbox ile dedup, ardından events tablosuna batch insert. Tek transaction. */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventIngestionService {

    private final EventJdbcRepository eventRepository;
    private final InboxJdbcRepository inboxRepository;
    private final IdempotencyService idempotencyService;
    private final EventMapper eventMapper;

    /** Batch'i tek transaction'da işler: inbox'ta var mı bak, yoksa inbox + events insert. Eklenen sayıyı döner. */
    @Transactional
    public int processBatch(List<EventRequest> events) {
        if (events.isEmpty()) {
            return 0;
        }

        // Idempotency key'leri üret, mevcut olanları kontrol et
        Map<String, EventRequest> keyToEvent = new LinkedHashMap<>();
        for (EventRequest event : events) {
            String key = idempotencyService.generateKey(event);
            keyToEvent.put(key, event);
        }

        Set<String> existingKeys = inboxRepository.findExistingKeys(keyToEvent.keySet());
        if (!existingKeys.isEmpty()) {
            log.debug("Deduplicating {} out of {} events", existingKeys.size(), events.size());
        }

        // Sadece yeni event'ler
        Map<String, EventRequest> newEvents = keyToEvent.entrySet().stream()
                .filter(entry -> !existingKeys.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));

        if (newEvents.isEmpty()) {
            log.debug("All {} events were duplicates, skipping batch", events.size());
            return 0;
        }

        // Inbox'a yaz
        inboxRepository.batchInsert(newEvents.keySet());

        // Domain'e çevir ve event'leri toplu insert et
        List<Event> domainEvents = newEvents.entrySet().stream()
                .map(entry -> eventMapper.toEvent(entry.getValue(), entry.getKey()))
                .toList();
        eventRepository.batchInsert(domainEvents);

        log.info("Processed batch: {} new events inserted, {} duplicates skipped",
                newEvents.size(), existingKeys.size());
        return newEvents.size();
    }

    /** Tek event; batch pipeline'a yönlendirir. */
    @Transactional
    public boolean processSingle(EventRequest event) {
        return processBatch(List.of(event)) > 0;
    }

}
