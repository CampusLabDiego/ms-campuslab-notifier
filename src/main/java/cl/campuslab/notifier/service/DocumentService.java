package cl.campuslab.notifier.service;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import cl.campuslab.notifier.messaging.BookingTaskMessage;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DocumentService {

    private static final float MARGIN = 50;
    private static final float FONT_SIZE = 11;
    private static final float LINE_HEIGHT = 17;

    private final Path directory;
    private final ZoneId zone;

    private final DateTimeFormatter dateFormat =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public DocumentService(
            @Value("${campuslab.documents.directory}")
            String directory,
            @Value("${campuslab.documents.zone}")
            String zone) {

        this.directory = Path.of(directory)
                .toAbsolutePath()
                .normalize();

        this.zone = ZoneId.of(zone);
    }

    public String generate(BookingTaskMessage task)
            throws IOException {

        String prefix = switch (task.taskType()) {
            case PREPARATION -> "preparacion";
            case VOUCHER -> "comprobante";
            case EMAIL -> throw new IllegalArgumentException(
                    "Una tarea de correo no genera este documento.");
        };

        Files.createDirectories(directory);

        String filename = prefix + "-" + task.taskId() + ".pdf";
        Path destination = directory.resolve(filename);

        // El nombre depende de la tarea, no de un nuevo intento.
        if (Files.isRegularFile(destination)) {
            return filename;
        }

        Path temporary = Files.createTempFile(
                directory,
                "campuslab-",
                ".tmp");

        try {
            writePdf(task, temporary);

            try {
                Files.move(
                        temporary,
                        destination,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);

            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(
                        temporary,
                        destination,
                        StandardCopyOption.REPLACE_EXISTING);
            }

            return filename;

        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void writePdf(
            BookingTaskMessage task,
            Path destination) throws IOException {

        try (PDDocument document = new PDDocument()) {
            PDType1Font font = new PDType1Font(
                    Standard14Fonts.FontName.HELVETICA);

            PDType1Font heading = new PDType1Font(
                    Standard14Fonts.FontName.HELVETICA_BOLD);

            boolean preparation =
                    task.taskType()
                            == BookingTaskMessage.TaskType.PREPARATION;

            String title = preparation
                    ? "Ticket de preparación"
                    : "Comprobante de reserva";

            document.getDocumentInformation()
                    .setTitle(title + " #" + task.bookingId());

            document.getDocumentInformation()
                    .setAuthor("CampusLab");

            List<String> paragraphs = new ArrayList<>();

            paragraphs.add(title);
            paragraphs.add("Reserva: #" + task.bookingId());
            paragraphs.add("Recurso: " + task.resourceName());
            paragraphs.add("Usuario: " + task.userEmail());
            paragraphs.add("Cantidad: " + task.quantity());

            paragraphs.add(
                    "Inicio: "
                            + task.startDateTime()
                                    .atOffset(ZoneOffset.UTC)
                                    .atZoneSameInstant(zone)
                                    .format(dateFormat));

            paragraphs.add(
                    "Término: "
                            + task.endDateTime()
                                    .atOffset(ZoneOffset.UTC)
                                    .atZoneSameInstant(zone)
                                    .format(dateFormat));

            paragraphs.add("Zona horaria: " + zone.getId());
            paragraphs.add("Motivo: " + task.purpose());
            paragraphs.add(
                    "Estado registrado: "
                            + task.status().replace('_', ' '));

            paragraphs.add(
                    "Evento: "
                            + eventName(task.eventType()));

            paragraphs.add(
                    "Fecha del evento: "
                            + task.occurredAt()
                                    .atZone(zone)
                                    .format(dateFormat));

            if (preparation) {
                paragraphs.add(
                        "Preparación requerida: verificar el recurso, "
                                + "la cantidad y las condiciones de entrega.");

                paragraphs.add("Responsable: __________________________");
                paragraphs.add("Observaciones: _________________________");
            }

            paragraphs.add("Identificador de tarea: " + task.taskId());

            float maxWidth =
                    PDRectangle.A4.getWidth() - 2 * MARGIN;

            List<String> lines = new ArrayList<>();

            for (String paragraph : paragraphs) {
                lines.addAll(
                        wrap(safeText(paragraph, font), font, maxWidth));

                lines.add("");
            }

            int index = 0;

            while (index < lines.size()) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);

                try (PDPageContentStream content =
                             new PDPageContentStream(document, page)) {

                    content.beginText();
                    content.setFont(heading, 20);
                    content.newLineAtOffset(
                            MARGIN,
                            PDRectangle.A4.getHeight() - MARGIN);

                    content.showText("CampusLab");
                    content.endText();

                    content.beginText();
                    content.setFont(font, FONT_SIZE);
                    content.setLeading(LINE_HEIGHT);
                    content.newLineAtOffset(
                            MARGIN,
                            PDRectangle.A4.getHeight() - MARGIN - 35);

                    float y =
                            PDRectangle.A4.getHeight() - MARGIN - 35;

                    while (index < lines.size() && y >= MARGIN) {
                        content.showText(lines.get(index++));
                        content.newLine();
                        y -= LINE_HEIGHT;
                    }

                    content.endText();
                }
            }

            document.save(destination.toFile());
        }
    }

    private List<String> wrap(
            String text,
            PDType1Font font,
            float maxWidth) throws IOException {

        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            String candidate = current.toString() + character;

            float width =
                    font.getStringWidth(candidate) / 1000 * FONT_SIZE;

            if (width > maxWidth && !current.isEmpty()) {
                lines.add(current.toString());
                current.setLength(0);
            }

            current.append(character);
        }

        if (!current.isEmpty()) {
            lines.add(current.toString());
        }

        return lines;
    }

    private String safeText(
            String text,
            PDType1Font font) throws IOException {

        String normalized = Normalizer.normalize(
                text,
                Normalizer.Form.NFC);

        StringBuilder result = new StringBuilder();

        for (int codePoint : normalized.codePoints().toArray()) {
            if (Character.isISOControl(codePoint)) {
                result.append(' ');
                continue;
            }

            String character = new String(
                    Character.toChars(codePoint));

            try {
                font.encode(character);
                result.append(character);

            } catch (IllegalArgumentException exception) {
                result.append('?');
            }
        }

        return result.toString();
    }

    private String eventName(BookingTaskMessage.EventType event) {
        return switch (event) {
            case REQUESTED -> "Solicitud registrada";
            case APPROVED -> "Reserva aprobada";
            case PREPARATION_STARTED -> "Preparación iniciada";
            case ARRIVAL_CONFIRMED -> "Uso registrado";
            case RETURNED -> "Devolución registrada";
            case CANCELLED -> "Reserva cancelada";
        };
    }
}