package com.baykanat.insider.assessment.domain.mapper;

import com.baykanat.insider.assessment.api.dto.EventRequest;
import com.baykanat.insider.assessment.domain.model.Event;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** EventRequest ↔ Event ve Kafka record value → EventRequest dönüşümleri. MapStruct + JSONB için Jackson. */
@Mapper(componentModel = "spring")
public interface EventMapper {

    /** JSONB alanları için paylaşılan ObjectMapper. */
    ObjectMapper JSON_MAPPER = new ObjectMapper();

    /** EventRequest → Event; timestamp→eventDate, tags/metadata→JSON string. */
    @Mapping(target = "eventTimestamp", source = "request.timestamp")
    @Mapping(target = "eventDate", source = "request.timestamp", qualifiedByName = "epochToLocalDate")
    @Mapping(target = "tags", source = "request.tags", qualifiedByName = "toJsonString")
    @Mapping(target = "metadata", source = "request.metadata", qualifiedByName = "toJsonString")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Event toEvent(EventRequest request, String idempotencyKey);

    /** Kafka value EventRequest ise döner, değilse Map vb. üzerinden EventRequest'e çevirir. */
    default EventRequest fromRecordValue(Object value) {
        if (value instanceof EventRequest eventRequest) {
            return eventRequest;
        }
        return JSON_MAPPER.convertValue(value, EventRequest.class);
    }

    /** Epoch saniye → UTC LocalDate (event_date partition için). */
    @Named("epochToLocalDate")
    default LocalDate epochToLocalDate(Long epochSeconds) {
        if (epochSeconds == null) return null;
        return Instant.ofEpochSecond(epochSeconds)
                .atOffset(ZoneOffset.UTC)
                .toLocalDate();
    }

    /** List/Map vb. → JSON string (tags, metadata JSONB için). */
    @Named("toJsonString")
    default String toJsonString(Object obj) {
        if (obj == null) return null;
        try {
            return JSON_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
