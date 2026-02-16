package com.baykanat.insider.assessment.domain.service;

import com.baykanat.insider.assessment.api.dto.EventRequest;
import com.baykanat.insider.assessment.domain.mapper.EventMapper;
import com.baykanat.insider.assessment.infrastructure.persistence.EventJdbcRepository;
import com.baykanat.insider.assessment.infrastructure.persistence.InboxJdbcRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EventIngestionService.
 *
 * <p>Verifies the batch processing logic with mocked repositories:
 * <ul>
 *   <li>Deduplication via inbox check</li>
 *   <li>Proper delegation to event repository</li>
 *   <li>Edge cases (empty batch, all duplicates)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class EventIngestionServiceTest {

    @Mock
    private EventJdbcRepository eventRepository;

    @Mock
    private InboxJdbcRepository inboxRepository;

    private EventIngestionService service;

    @BeforeEach
    void setUp() {
        IdempotencyService idempotencyService = new IdempotencyService();
        EventMapper eventMapper = Mappers.getMapper(EventMapper.class);
        service = new EventIngestionService(eventRepository, inboxRepository,
                idempotencyService, eventMapper);
    }

    @Test
    @DisplayName("Process batch - new events should be inserted into inbox and events tables")
    void processBatchInsertsNewEvents() {
        EventRequest event = EventRequest.builder()
                .eventName("product_view")
                .userId("user_123")
                .timestamp(1771156800L)
                .campaignId("cmp_987")
                .build();

        when(inboxRepository.findExistingKeys(anyCollection())).thenReturn(Collections.emptySet());

        int inserted = service.processBatch(List.of(event));

        assertThat(inserted).isEqualTo(1);
        verify(inboxRepository).batchInsert(anyCollection());
        verify(eventRepository).batchInsert(anyList());
    }

    @Test
    @DisplayName("Process batch - duplicate events should be skipped")
    void processBatchSkipsDuplicates() {
        EventRequest event = EventRequest.builder()
                .eventName("product_view")
                .userId("user_123")
                .timestamp(1771156800L)
                .campaignId("cmp_987")
                .build();

        // Simulate: this event's key already exists in inbox
        IdempotencyService idempotencyService = new IdempotencyService();
        String key = idempotencyService.generateKey(event);
        when(inboxRepository.findExistingKeys(anyCollection())).thenReturn(Set.of(key));

        int inserted = service.processBatch(List.of(event));

        assertThat(inserted).isEqualTo(0);
        verify(eventRepository, never()).batchInsert(anyList());
    }

    @Test
    @DisplayName("Process batch - mixed new and duplicate events")
    void processBatchHandlesMixedBatch() {
        EventRequest newEvent = EventRequest.builder()
                .eventName("product_view")
                .userId("user_123")
                .timestamp(1771156800L)
                .campaignId("cmp_987")
                .build();

        EventRequest duplicateEvent = EventRequest.builder()
                .eventName("add_to_cart")
                .userId("user_456")
                .timestamp(1771156810L)
                .campaignId("cmp_789")
                .build();

        IdempotencyService idempotencyService = new IdempotencyService();
        String dupKey = idempotencyService.generateKey(duplicateEvent);
        when(inboxRepository.findExistingKeys(anyCollection())).thenReturn(Set.of(dupKey));

        int inserted = service.processBatch(List.of(newEvent, duplicateEvent));

        assertThat(inserted).isEqualTo(1);
        verify(eventRepository).batchInsert(argThat(list -> list.size() == 1));
    }

    @Test
    @DisplayName("Process batch - empty list should return 0 without DB calls")
    void processBatchHandlesEmptyList() {
        int inserted = service.processBatch(Collections.emptyList());

        assertThat(inserted).isEqualTo(0);
        verifyNoInteractions(eventRepository, inboxRepository);
    }
}
