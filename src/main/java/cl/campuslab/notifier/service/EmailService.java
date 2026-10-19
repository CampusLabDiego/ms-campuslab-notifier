package cl.campuslab.notifier.service;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import cl.campuslab.notifier.messaging.BookingTaskMessage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String from;
    private final ZoneId zone;
    private final String testRecipient;

    private final DateTimeFormatter dateFormat =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public EmailService(
            JavaMailSender mailSender,
            @Value("${campuslab.mail.from}")
            String from,
            @Value("${campuslab.documents.zone}")
            String zone,
            @Value("${MAIL_TEST_RECIPIENT:}")
            String testRecipient) {

        this.mailSender = mailSender;
        this.from = from;
        this.zone = ZoneId.of(zone);
        this.testRecipient = testRecipient.trim();
    }

    public void send(BookingTaskMessage task)
            throws MessagingException {

        if (task.taskType() != BookingTaskMessage.TaskType.EMAIL) {
            throw new IllegalArgumentException(
                    "La tarea recibida no corresponde a un correo.");
        }

        boolean testMode = !testRecipient.isBlank();

        String recipient = testMode
                ? testRecipient
                : task.userEmail();

        String event = eventName(task.eventType());

        String start = task.startDateTime()
                .atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(zone)
                .format(dateFormat);

        String end = task.endDateTime()
                .atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(zone)
                .format(dateFormat);

        String occurred = task.occurredAt()
                .atZone(zone)
                .format(dateFormat);

        String notice = testMode
                ? """
                  MODO DE PRUEBA
                  Este aviso fue redirigido a un buzón de pruebas.
                  Destinatario original: %s

                  """.formatted(task.userEmail())
                : "";

        String body = notice + """
                CampusLab

                %s

                Reserva: #%s
                Recurso: %s
                Cantidad: %s
                Inicio: %s
                Término: %s
                Zona horaria: %s

                Motivo: %s
                Estado registrado: %s
                Fecha del evento: %s

                Puedes consultar el estado actual de tu reserva
                iniciando sesión en CampusLab.

                Identificador de tarea: %s
                """.formatted(
                event,
                task.bookingId(),
                task.resourceName(),
                task.quantity(),
                start,
                end,
                zone.getId(),
                task.purpose(),
                task.status().replace('_', ' '),
                occurred,
                task.taskId()
        );

        MimeMessage message = mailSender.createMimeMessage();

        MimeMessageHelper helper = new MimeMessageHelper(
                message,
                false,
                StandardCharsets.UTF_8.name());

        helper.setFrom(from);
        helper.setTo(recipient);

        helper.setSubject(
                (testMode ? "[PRUEBA] " : "")
                        + "CampusLab - Reserva #"
                        + task.bookingId()
                        + " - "
                        + event);

        helper.setText(body, false);

        message.setHeader(
                "X-CampusLab-Task-ID",
                task.taskId().toString());

        mailSender.send(message);
    }

    private String eventName(BookingTaskMessage.EventType event) {
        return switch (event) {
            case REQUESTED -> "Solicitud de reserva registrada";
            case APPROVED -> "Reserva aprobada";
            case PREPARATION_STARTED -> "Preparación iniciada";
            case ARRIVAL_CONFIRMED -> "Uso registrado";
            case RETURNED -> "Devolución registrada";
            case CANCELLED -> "Reserva cancelada";
        };
    }
}