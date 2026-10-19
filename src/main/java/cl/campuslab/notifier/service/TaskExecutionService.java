package cl.campuslab.notifier.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;

import jakarta.mail.MessagingException;
import jakarta.validation.Validator;

import cl.campuslab.notifier.entity.TaskExecution;
import cl.campuslab.notifier.messaging.BookingTaskMessage;
import cl.campuslab.notifier.repository.TaskExecutionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.json.JsonMapper;

@Service
public class TaskExecutionService {

    private final TaskExecutionRepository repository;
    private final EmailService emails;
    private final DocumentService documents;
    private final Validator validator;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate transaction;

    public TaskExecutionService(
            TaskExecutionRepository repository,
            EmailService emails,
            DocumentService documents,
            Validator validator,
            JsonMapper jsonMapper,
            PlatformTransactionManager transactionManager) {

        this.repository = repository;
        this.emails = emails;
        this.documents = documents;
        this.validator = validator;
        this.jsonMapper = jsonMapper;

        this.transaction = new TransactionTemplate(transactionManager);

        this.transaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public boolean process(
            BookingTaskMessage message,
            BookingTaskMessage.TaskType expectedType) {

        validate(message, expectedType);

        String payload = jsonMapper.writeValueAsString(message);
        String taskId = message.taskId().toString();

        // Registra la tarea antes de ejecutar efectos externos.
        transaction.executeWithoutResult(
                status -> register(message, payload));

        try {
            Boolean executed = transaction.execute(status -> {
                TaskExecution task = locked(taskId);

                if (!task.getPayload().equals(payload)) {
                    throw new IllegalArgumentException(
                            "El identificador de tarea ya existe "
                                    + "con otros datos.");
                }

                if (task.getState() == TaskExecution.State.COMPLETED) {
                    return false;
                }

                task.setState(TaskExecution.State.PENDING);
                task.setAttempts(task.getAttempts() + 1);

                String filename = execute(message);

                task.setOutputFilename(filename);
                task.setState(TaskExecution.State.COMPLETED);
                task.setCompletedAt(Instant.now());
                task.setLastError(null);

                repository.saveAndFlush(task);

                return true;
            });

            return Boolean.TRUE.equals(executed);

        } catch (RuntimeException exception) {
            try {
                recordAttemptFailure(taskId, exception);
            } catch (RuntimeException recordingFailure) {
                exception.addSuppressed(recordingFailure);
            }

            throw exception;
        }
    }

    public void markFailed(String taskId, String detail) {
        transaction.executeWithoutResult(status -> {
            TaskExecution task = repository
                    .findLockedByTaskId(taskId)
                    .orElse(null);

            if (task == null
                    || task.getState() == TaskExecution.State.COMPLETED) {
                return;
            }

            task.setState(TaskExecution.State.FAILED);
            task.setLastError(shortDetail(detail));

            repository.save(task);
        });
    }

    private void register(
            BookingTaskMessage message,
            String payload) {

        String taskId = message.taskId().toString();

        TaskExecution existing = repository
                .findLockedByTaskId(taskId)
                .orElse(null);

        if (existing != null) {
            if (!existing.getPayload().equals(payload)) {
                throw new IllegalArgumentException(
                        "El identificador de tarea ya existe "
                                + "con otros datos.");
            }

            return;
        }

        TaskExecution task = new TaskExecution();

        task.setTaskId(taskId);
        task.setBookingId(message.bookingId());
        task.setUserId(message.userId());
        task.setUserEmail(message.userEmail());
        task.setTaskType(message.taskType());
        task.setEventType(message.eventType());
        task.setPayload(payload);
        task.setState(TaskExecution.State.PENDING);
        task.setAttempts(0);

        repository.saveAndFlush(task);
    }

    private String execute(BookingTaskMessage message) {
        try {
            return switch (message.taskType()) {
                case EMAIL -> {
                    emails.send(message);
                    yield null;
                }

                case PREPARATION, VOUCHER ->
                        documents.generate(message);
            };

        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "No se pudo generar el documento.",
                    exception);

        } catch (MessagingException exception) {
            throw new IllegalStateException(
                    "No se pudo construir el correo.",
                    exception);
        }
    }

    private void recordAttemptFailure(
            String taskId,
            RuntimeException exception) {

        transaction.executeWithoutResult(status -> {
            TaskExecution task = locked(taskId);

            if (task.getState() == TaskExecution.State.COMPLETED) {
                return;
            }

            // El intento de la transaccion fallida fue revertido.
            task.setAttempts(task.getAttempts() + 1);
            task.setLastError(
                    "Fallo de ejecucion: "
                            + exception.getClass().getSimpleName());

            repository.save(task);
        });
    }

    private TaskExecution locked(String taskId) {
        return repository.findLockedByTaskId(taskId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "La tarea registrada no existe."));
    }

    private void validate(
            BookingTaskMessage message,
            BookingTaskMessage.TaskType expectedType) {

        if (message == null
                || !validator.validate(message).isEmpty()) {
            throw new IllegalArgumentException(
                    "La tarea tiene datos invalidos.");
        }

        if (message.taskType() != expectedType) {
            throw new IllegalArgumentException(
                    "El tipo de tarea no corresponde a esta cola.");
        }

        if (!message.endDateTime().isAfter(message.startDateTime())) {
            throw new IllegalArgumentException(
                    "El horario de la tarea es invalido.");
        }

        if (expectedType == BookingTaskMessage.TaskType.PREPARATION
                && message.eventType()
                        != BookingTaskMessage.EventType.PREPARATION_STARTED) {

            throw new IllegalArgumentException(
                    "El ticket requiere un evento de preparacion.");
        }

        if (expectedType == BookingTaskMessage.TaskType.VOUCHER
                && message.eventType()
                        != BookingTaskMessage.EventType.APPROVED
                && message.eventType()
                        != BookingTaskMessage.EventType.RETURNED) {

            throw new IllegalArgumentException(
                    "El comprobante requiere aprobacion o devolucion.");
        }
    }

    private String shortDetail(String detail) {
        String value = detail == null || detail.isBlank()
                ? "La tarea no pudo completarse."
                : detail;

        return value.substring(0, Math.min(value.length(), 500));
    }
}