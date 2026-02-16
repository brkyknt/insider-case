package com.baykanat.insider.assessment.api.controller;

import com.baykanat.insider.assessment.api.dto.BulkEventRequest;
import com.baykanat.insider.assessment.api.dto.EventRequest;
import com.baykanat.insider.assessment.api.dto.EventResponse;
import com.baykanat.insider.assessment.infrastructure.kafka.EventKafkaProducer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** POST /events ve POST /events/bulk. Event Kafka'ya gönderilir, 202 döner; DB yazımı consumer'da. */
@Slf4j
@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
@Tag(name = "Event Ingestion", description = "Endpoints for ingesting events into the platform")
public class EventController {

    private final EventKafkaProducer kafkaProducer;

    /** Tek event alır, doğrular, Kafka'ya gönderir. Geçersiz payload → 400, geçerli → 202. */
    @PostMapping
    @Operation(summary = "Ingest a single event", description = "Accepts and queues a single event for async processing")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Event accepted for processing"),
            @ApiResponse(responseCode = "400", description = "Invalid event payload"),
            @ApiResponse(responseCode = "503", description = "Service temporarily unavailable (Kafka down)")
    })
    public ResponseEntity<EventResponse> ingestEvent(@Valid @RequestBody EventRequest event) throws Exception {
        log.debug("Received event: event_name={}, user_id={}", event.getEventName(), event.getUserId());

        kafkaProducer.send(event);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(EventResponse.builder()
                        .status("accepted")
                        .acceptedCount(1)
                        .message("Event queued for processing")
                        .build());
    }

    /** En fazla 1000 event kabul eder; hepsi doğrulanıp Kafka'ya paralel gönderilir. */
    @PostMapping("/bulk")
    @Operation(summary = "Bulk ingest events", description = "Accepts up to 1000 events for async processing")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Events accepted for processing"),
            @ApiResponse(responseCode = "400", description = "Invalid event payload(s)"),
            @ApiResponse(responseCode = "503", description = "Service temporarily unavailable")
    })
    public ResponseEntity<EventResponse> ingestBulkEvents(@Valid @RequestBody BulkEventRequest bulkRequest) throws Exception {
        log.debug("Received bulk request with {} events", bulkRequest.getEvents().size());

        kafkaProducer.sendBatch(bulkRequest.getEvents());

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(EventResponse.builder()
                        .status("accepted")
                        .acceptedCount(bulkRequest.getEvents().size())
                        .message("Events queued for processing")
                        .build());
    }
}
