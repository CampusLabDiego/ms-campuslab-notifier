package cl.campuslab.notifier.messaging;

import java.io.IOException;

import com.rabbitmq.client.Channel;

import cl.campuslab.notifier.audit.AuditService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;

import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;

@Component
public class BookingAuditListener {

    private static final Logger log =
            LoggerFactory.getLogger(BookingAuditListener.class);

    private static final int MAX_ATTEMPTS = 3;

    private final AuditService service;

    public BookingAuditListener(AuditService service) {
        this.service = service;
    }

    @RabbitListener(queues = RabbitNames.AUDIT_QUEUE)
    public void receive(
            BookingAuditMessage event,
            Message message,
            Channel channel
    ) throws IOException {

        long deliveryTag = message
                .getMessageProperties()
                .getDeliveryTag();

        String eventId = event == null || event.eventId() == null
                ? message.getMessageProperties().getMessageId()
                : event.eventId().toString();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            boolean inserted;

            try {
                inserted = service.process(event);

            } catch (RuntimeException exception) {
                if (
                        isRetryable(exception)
                        && attempt < MAX_ATTEMPTS
                ) {
                    log.warn(
                            "Reintentando auditoría: evento={}, intento={}",
                            eventId,
                            attempt
                    );

                    try {
                        Thread.sleep(500L * attempt);

                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();

                        channel.basicNack(
                                deliveryTag,
                                false,
                                false
                        );

                        log.warn(
                                "Auditoría enviada a DLQ por interrupción: "
                                        + "evento={}",
                                eventId
                        );

                        return;
                    }

                    continue;
                }

                log.error(
                        "Auditoría enviada a DLQ: "
                                + "evento={}, intento={}, causa={}",
                        eventId,
                        attempt,
                        exception.getClass().getSimpleName()
                );

                channel.basicNack(deliveryTag, false, false);
                return;
            }

            // process() ya terminó y confirmó su transacción.
            channel.basicAck(deliveryTag, false);

            log.info(
                    "Auditoría confirmada: evento={}, reserva={}, duplicada={}",
                    eventId,
                    event.bookingId(),
                    !inserted
            );

            return;
        }
    }

    private boolean isRetryable(Throwable error) {
        Throwable current = error;

        for (
                int depth = 0;
                current != null && depth < 10;
                depth++, current = current.getCause()
        ) {
            if (
                    current instanceof TransientDataAccessException
                    || current instanceof RecoverableDataAccessException
                    || current instanceof DataAccessResourceFailureException
                    || current instanceof CannotCreateTransactionException
                    || current instanceof DataIntegrityViolationException
            ) {
                return true;
            }
        }

        return false;
    }
}