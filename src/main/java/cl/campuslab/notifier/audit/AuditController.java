package cl.campuslab.notifier.audit;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService service;

    public AuditController(AuditService service) {
        this.service = service;
    }

    @GetMapping
    public List<AuditEventResponse> findEvents(
            @RequestParam(
                    name = "bookingId",
                    required = false
            )
            Long bookingId
    ) {
        return service.findEvents(bookingId);
    }
}