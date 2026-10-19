package cl.campuslab.notifier.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import cl.campuslab.notifier.dto.TaskExecutionResponse;
import cl.campuslab.notifier.entity.TaskExecution;
import cl.campuslab.notifier.repository.TaskExecutionRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TaskQueryService {

    private final TaskExecutionRepository repository;
    private final Path directory;

    public TaskQueryService(
            TaskExecutionRepository repository,
            @Value("${campuslab.documents.directory}")
            String directory) {

        this.repository = repository;

        this.directory = Path.of(directory)
                .toAbsolutePath()
                .normalize();
    }

    @Transactional(readOnly = true)
    public List<TaskExecutionResponse> findAll(Jwt jwt) {
        List<TaskExecution> tasks = canManage(jwt)
                ? repository.findTop100ByOrderByCreatedAtDesc()
                : repository.findTop100ByUserIdOrderByCreatedAtDesc(
                        ownerId(jwt));

        return tasks.stream()
                .map(TaskExecutionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TaskExecutionResponse findById(String taskId, Jwt jwt) {
        return TaskExecutionResponse.from(
                findAuthorized(taskId, jwt));
    }

    @Transactional(readOnly = true)
    public Download download(String taskId, Jwt jwt) {
        TaskExecution task = findAuthorized(taskId, jwt);

        if (task.getState() != TaskExecution.State.COMPLETED
                || task.getOutputFilename() == null
                || task.getOutputFilename().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "La tarea no tiene un documento disponible.");
        }

        String filename = task.getOutputFilename();

        Path file = directory.resolve(filename).normalize();

        if (!directory.equals(file.getParent())
                || Files.isSymbolicLink(file)
                || !Files.isRegularFile(file)) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "El documento no está disponible.");
        }

        try {
            return new Download(
                    new FileSystemResource(file),
                    filename,
                    Files.size(file)
            );

        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo acceder al documento.");
        }
    }

    private TaskExecution findAuthorized(String taskId, Jwt jwt) {
        String normalizedId = normalizeId(taskId);

        if (canManage(jwt)) {
            return repository.findById(normalizedId)
                    .orElseThrow(this::notFound);
        }

        // El propietario se obtiene del token, nunca de un parametro.
        return repository.findByTaskIdAndUserId(
                        normalizedId,
                        ownerId(jwt))
                .orElseThrow(this::notFound);
    }

    private boolean canManage(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");

        return roles != null
                && (roles.contains("Admin")
                        || roles.contains("Operador"));
    }

    private String ownerId(Jwt jwt) {
        String owner = jwt.getClaimAsString("oid");

        if (owner == null || owner.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "El token no identifica al propietario.");
        }

        return owner;
    }

    private String normalizeId(String taskId) {
        try {
            UUID id = UUID.fromString(taskId);

            if (!id.toString().equalsIgnoreCase(taskId)) {
                throw new IllegalArgumentException();
            }

            return id.toString();

        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El identificador de tarea es inválido.");
        }
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "La tarea no está disponible.");
    }

    public record Download(
            FileSystemResource resource,
            String filename,
            long size
    ) {
    }
}