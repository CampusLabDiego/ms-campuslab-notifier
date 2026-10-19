package cl.campuslab.notifier.audit;

import java.time.Instant;

import cl.campuslab.notifier.messaging.BookingAuditMessage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "booking_audit_events",
        indexes = {
                @Index(
                        name = "idx_audit_booking_time",
                        columnList = "booking_id,occurred_at"
                ),
                @Index(
                        name = "idx_audit_time",
                        columnList = "occurred_at"
                )
        }
)
@Getter
@NoArgsConstructor
public class AuditEvent {

    @Id
    @Column(name = "event_id", length = 36, nullable = false)
    private String eventId;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 40, nullable = false)
    private BookingAuditMessage.EventType eventType;

    @Column(name = "previous_status", length = 30)
    private String previousStatus;

    @Column(name = "status", length = 30, nullable = false)
    private String status;

    @Column(name = "target_status", length = 30)
    private String targetStatus;

    @Column(name = "actor_id", length = 100, nullable = false)
    private String actorId;

    @Column(name = "actor_email", length = 320)
    private String actorEmail;

    @Column(name = "owner_id", length = 100, nullable = false)
    private String ownerId;

    @Column(name = "resource_name", length = 255, nullable = false)
    private String resourceName;

    @Column(name = "operation_id", length = 36)
    private String operationId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(
            name = "payload",
            nullable = false,
            columnDefinition = "LONGTEXT"
    )
    private String payload;

    public static AuditEvent from(
            BookingAuditMessage message,
            String payload
    ) {
        AuditEvent event = new AuditEvent();

        event.eventId = message.eventId().toString();
        event.bookingId = message.bookingId();
        event.eventType = message.eventType();
        event.previousStatus = message.previousStatus();
        event.status = message.status();
        event.targetStatus = message.targetStatus();
        event.actorId = message.actorId();
        event.actorEmail = message.actorEmail();
        event.ownerId = message.ownerId();
        event.resourceName = message.resourceName();

        event.operationId = message.operationId() == null
                ? null
                : message.operationId().toString();

        event.occurredAt = message.occurredAt();
        event.receivedAt = Instant.now();
        event.payload = payload;

        return event;
    }
}