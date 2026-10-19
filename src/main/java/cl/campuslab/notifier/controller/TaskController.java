package cl.campuslab.notifier.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;

import cl.campuslab.notifier.dto.TaskExecutionResponse;
import cl.campuslab.notifier.service.TaskQueryService;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class TaskController {

    private final TaskQueryService service;

    public TaskController(TaskQueryService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<TaskExecutionResponse>> findAll(
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.findAll(jwt));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<TaskExecutionResponse> findById(
            @PathVariable("taskId") String taskId,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.findById(taskId, jwt));
    }

    @GetMapping(
            value = "/{taskId}/document",
            produces = MediaType.APPLICATION_PDF_VALUE
    )
    public ResponseEntity<Resource> download(
            @PathVariable("taskId") String taskId,
            @AuthenticationPrincipal Jwt jwt) {

        TaskQueryService.Download document =
                service.download(taskId, jwt);

        ContentDisposition disposition =
                ContentDisposition.attachment()
                        .filename(
                                document.filename(),
                                StandardCharsets.UTF_8)
                        .build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(document.size())
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        disposition.toString())
                .body(document.resource());
    }
}