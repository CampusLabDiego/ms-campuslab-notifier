package cl.campuslab.notifier.dto;

import java.time.Instant;

import cl.campuslab.notifier.entity.TaskExecution;
import cl.campuslab.notifier.messaging.BookingTaskMessage;

public record TaskExecutionResponse(

        String taskId,

        Long bookingId,

        String userEmail,

        BookingTaskMessage.TaskType taskType,

        BookingTaskMessage.EventType eventType,

        TaskExecution.State state,

        int attempts,

        Instant createdAt,

        Instant completedAt,

        String lastError,

        boolean documentAvailable,

        String downloadPath

) {

    public static TaskExecutionResponse from(TaskExecution task) {
        boolean documentAvailable =
                task.getState() == TaskExecution.State.COMPLETED
                        && task.getOutputFilename() != null
                        && !task.getOutputFilename().isBlank();

        String downloadPath = documentAvailable
                ? "/api/notifications/"
                        + task.getTaskId()
                        + "/document"
                : null;

        return new TaskExecutionResponse(
                task.getTaskId(),
                task.getBookingId(),
                task.getUserEmail(),
                task.getTaskType(),
                task.getEventType(),
                task.getState(),
                task.getAttempts(),
                task.getCreatedAt(),
                task.getCompletedAt(),
                task.getLastError(),
                documentAvailable,
                downloadPath
        );
    }
}