package cl.campuslab.notifier.audit;

import java.util.List;

import cl.campuslab.notifier.messaging.BookingAuditMessage;

import jakarta.validation.Validator;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.json.JsonMapper;

@Service
public class AuditService {

    private final AuditEventRepository repository;
    private final Validator validator;
    private final JsonMapper jsonMapper;

    public AuditService(
            AuditEventRepository repository,
            Validator validator,
            JsonMapper jsonMapper
    ) {
        this.repository = repository;
        this.validator = validator;
        this.jsonMapper = jsonMapper;
    }

    @Transactional
    public boolean process(BookingAuditMessage message) {
        if (
                message == null
                || !validator.validate(message).isEmpty()
        ) {
            throw new IllegalArgumentException(
                    "El evento de auditoría contiene datos inválidos."
            );
        }

        String payload;

        try {
            payload = jsonMapper.writeValueAsString(message);

        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "No se pudo interpretar el evento de auditoría.",
                    exception
            );
        }

        String eventId = message.eventId().toString();

        AuditEvent existing = repository
                .findById(eventId)
                .orElse(null);

        if (existing != null) {
            if (!existing.getPayload().equals(payload)) {
                throw new IllegalArgumentException(
                        "El identificador del evento ya existe "
                                + "con un contenido diferente."
                );
            }

            // Redelivery del mismo evento: no inserta otra fila.
            return false;
        }

        repository.saveAndFlush(
                AuditEvent.from(message, payload)
        );

        return true;
    }

    @Transactional(readOnly = true)
    public List<AuditEventResponse> findEvents(Long bookingId) {
        if (bookingId != null && bookingId < 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El número de reserva debe ser positivo."
            );
        }

        List<AuditEvent> events = bookingId == null
                ? repository
                    .findTop500ByOrderByOccurredAtDescReceivedAtDescEventIdAsc()
                : repository
                    .findTop500ByBookingIdOrderByOccurredAtDescReceivedAtDescEventIdAsc(
                            bookingId
                    );

        return events.stream()
                .map(AuditEventResponse::from)
                .toList();
    }
}