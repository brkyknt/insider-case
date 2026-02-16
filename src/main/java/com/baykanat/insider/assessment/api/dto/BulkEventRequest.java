package com.baykanat.insider.assessment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Toplu event isteği; en fazla 1000 event, her biri @Valid ile doğrulanır. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Bulk event ingestion payload")
public class BulkEventRequest {

    @NotEmpty(message = "events list must not be empty")
    @Size(max = 1000, message = "Maximum 1000 events per bulk request")
    @Valid
    @Schema(description = "List of events to ingest")
    private List<EventRequest> events;
}
