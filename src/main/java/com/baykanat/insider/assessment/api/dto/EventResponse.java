package com.baykanat.insider.assessment.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Event ingestion yanıtı: 202 ile status ve kabul edilen event sayısı. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response for event ingestion")
public class EventResponse {

    @Schema(description = "Status message", example = "accepted")
    private String status;

    @Schema(description = "Number of events accepted", example = "1")
    private int acceptedCount;

    @Schema(description = "Additional message", example = "Events queued for processing")
    private String message;
}
