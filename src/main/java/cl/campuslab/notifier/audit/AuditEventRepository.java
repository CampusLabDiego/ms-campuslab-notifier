package cl.campuslab.notifier.audit;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository
        extends JpaRepository<AuditEvent, String> {

    List<AuditEvent>
    findTop500ByOrderByOccurredAtDescReceivedAtDescEventIdAsc();

    List<AuditEvent>
    findTop500ByBookingIdOrderByOccurredAtDescReceivedAtDescEventIdAsc(
            Long bookingId
    );
}