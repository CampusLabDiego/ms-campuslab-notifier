package cl.campuslab.notifier.messaging;

import java.util.concurrent.TimeUnit;

import cl.campuslab.notifier.audit.AuditService;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

@Component
public class BookingAuditKafkaListener {

    private final AuditService auditService;
    private final JsonMapper jsonMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public BookingAuditKafkaListener(
            AuditService auditService,
            JsonMapper jsonMapper,
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        this.auditService = auditService;
        this.jsonMapper = jsonMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
            topics = {
                    "bookings.events",
                    "audit.timeline"
            }
    )
    public void receive(
            String payload,
            Acknowledgment acknowledgment,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String sourceTopic
    ) {
        try {
            BookingAuditMessage message = jsonMapper.readValue(
                    payload,
                    BookingAuditMessage.class
            );

            auditService.process(message);

        } catch (Exception processingException) {
            String deadLetterTopic = sourceTopic + ".DLT";

            try {
                kafkaTemplate
                        .send(deadLetterTopic, payload)
                        .get(10, TimeUnit.SECONDS);

            } catch (Exception publishingException) {
                throw new IllegalStateException(
                        "No se pudo enviar el evento a "
                                + deadLetterTopic
                                + ". Kafka volverá a entregarlo.",
                        publishingException
                );
            }

            // Confirma el original solo después de enviar a la DLT.
            acknowledgment.acknowledge();
            return;
        }

        acknowledgment.acknowledge();
    }
}