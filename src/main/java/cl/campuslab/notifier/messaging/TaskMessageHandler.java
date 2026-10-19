package cl.campuslab.notifier.messaging;

import java.io.IOException;
import java.io.UncheckedIOException;

import com.rabbitmq.client.Channel;

import cl.campuslab.notifier.service.TaskExecutionService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.amqp.core.Message;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.mail.MailSendException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;

@Component
public class TaskMessageHandler {

    private static final Logger log =
            LoggerFactory.getLogger(TaskMessageHandler.class);

    private static final int MAX_ATTEMPTS = 3;

    private final TaskExecutionService service;

    public TaskMessageHandler(TaskExecutionService service) {
        this.service = service;
    }

    public void handle(
            BookingTaskMessage task,
            Message message,
            Channel channel,
            BookingTaskMessage.TaskType expectedType)
            throws IOException {

        long deliveryTag = message.getMessageProperties()
                .getDeliveryTag();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            boolean executed;

            try {
                executed = service.process(task, expectedType);

            } catch (RuntimeException exception) {
                boolean retry =
                        isRetryable(exception)
                                && attempt < MAX_ATTEMPTS;

                if (!retry) {
                    recordFinalFailure(task, expectedType, exception);

                    log.error(
                            "Tarea enviada a DLQ: mensaje={}, tipo={}, intento={}, causa={}",
                            message.getMessageProperties().getMessageId(),
                            expectedType,
                            attempt,
                            exception.getClass().getSimpleName());

                    channel.basicNack(deliveryTag, false, false);
                    return;
                }

                log.warn(
                        "Reintentando tarea: mensaje={}, tipo={}, intento={}",
                        message.getMessageProperties().getMessageId(),
                        expectedType,
                        attempt);

                try {
                    Thread.sleep(500L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();

                    channel.basicNack(deliveryTag, false, false);
                    return;
                }

                continue;
            }

            // Confirma despues de guardar el resultado de ejecucion.
            // Si falla el ACK, una reentrega reconocera la tarea
            // que ya este registrada como COMPLETED.
            channel.basicAck(deliveryTag, false);

            log.info(
                    "Tarea confirmada: tarea={}, reserva={}, tipo={}, duplicada={}",
                    task.taskId(),
                    task.bookingId(),
                    expectedType,
                    !executed);

            return;
        }
    }

    private void recordFinalFailure(
            BookingTaskMessage task,
            BookingTaskMessage.TaskType expectedType,
            RuntimeException exception) {

        // Un mensaje invalido o con datos contradictorios
        // no debe alterar una tarea legitima ya registrada.
        if (task == null
                || task.taskId() == null
                || task.taskType() != expectedType
                || exception instanceof IllegalArgumentException) {
            return;
        }

        try {
            service.markFailed(
                    task.taskId().toString(),
                    "Fallo definitivo: "
                            + exception.getClass().getSimpleName());

        } catch (RuntimeException recordingFailure) {
            log.warn(
                    "No se pudo registrar el fallo de la tarea {}: {}",
                    task.taskId(),
                    recordingFailure.getClass().getSimpleName());
        }
    }

    private boolean isRetryable(Throwable exception) {
        Throwable cause = exception;

        for (int depth = 0; cause != null && depth < 10; depth++) {
            if (cause instanceof TransientDataAccessException
                    || cause instanceof RecoverableDataAccessException
                    || cause instanceof DataAccessResourceFailureException
                    || cause instanceof DataIntegrityViolationException
                    || cause instanceof CannotCreateTransactionException
                    || cause instanceof MailSendException
                    || cause instanceof UncheckedIOException
                    || cause instanceof IOException) {
                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }
}