package com.rockettrading.rocket_trading.dto.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rockettrading.rocket_trading.repository.model.AuditLogRecord;

import java.time.Instant;

public record OrderTimelineEventResponse(
        long auditId,
        String entityName,
        long entityId,
        String actionType,
        Instant recordedAt,
        JsonNode stateBefore,
        JsonNode stateAfter
) {
    public static OrderTimelineEventResponse from(AuditLogRecord record, ObjectMapper objectMapper) {
        try {
            return new OrderTimelineEventResponse(
                    record.getAuditId(),
                    record.getEntityName(),
                    record.getEntityId(),
                    record.getActionType(),
                    record.getRecordedAt(),
                    parseNode(record.getStateBefore(), objectMapper),
                    parseNode(record.getStateAfter(), objectMapper)
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not deserialize audit timeline", exception);
        }
    }

    private static JsonNode parseNode(String json, ObjectMapper objectMapper) throws JsonProcessingException {
        if (json == null || json.isBlank()) {
            return null;
        }
        return objectMapper.readTree(json);
    }
}
