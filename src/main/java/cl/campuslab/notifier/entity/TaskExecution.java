package cl.campuslab.notifier.entity;

import java.time.Instant;

import cl.campuslab.notifier.messaging.BookingTaskMessage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "task_executions",
        indexes = {
                @Index(
                        name = "idx_task_execution_booking",
                        columnList = "booking_id"),
                @Index(
                        name = "idx_task_execution_owner",
                        columnList = "user_id"),
                @Index(
                        name = "idx_task_execution_state",
                        columnList = "state")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class TaskExecution {

    @Id
    @Column(name = "task_id", nullable = false, length = 36)
    private String taskId;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "user_email", nullable = false, length = 320)
    private String userEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 20)
    private BookingTaskMessage.TaskType taskType;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private BookingTaskMessage.EventType eventType;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private State state;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "output_filename", length = 255)
    private String outputFilename;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    private Long version;

    @PrePersist
    void initialize() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }

        if (state == null) {
            state = State.PENDING;
        }
    }

    public enum State {
        PENDING,
        COMPLETED,
        FAILED
    }
}