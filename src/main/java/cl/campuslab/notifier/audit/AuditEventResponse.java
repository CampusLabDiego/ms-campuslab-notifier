package cl.campuslab.notifier.audit;

import java.time.Instant;

public record AuditEventResponse(
        String eventId,
        Long bookingId,
        String eventType,
        String previousStatus,
        String status,
        String targetStatus,
        String actorId,
        String actorEmail,
        String ownerId,
        String resourceName,
        String operationId,
        Instant occurredAt,
        Instant receivedAt
) {

    public static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(
                event.getEventId(),
                event.getBookingId(),
                event.getEventType().name(),
                event.getPreviousStatus(),
                event.getStatus(),
                event.getTargetStatus(),
                event.getActorId(),
                event.getActorEmail(),
                event.getOwnerId(),
                event.getResourceName(),
                event.getOperationId(),
                event.getOccurredAt(),
                event.getReceivedAt()
        );
    }
}