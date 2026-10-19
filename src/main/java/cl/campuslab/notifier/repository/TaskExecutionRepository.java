package cl.campuslab.notifier.repository;

import java.util.List;
import java.util.Optional;

import cl.campuslab.notifier.entity.TaskExecution;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskExecutionRepository
        extends JpaRepository<TaskExecution, String> {

    List<TaskExecution> findTop100ByOrderByCreatedAtDesc();

    List<TaskExecution> findTop100ByUserIdOrderByCreatedAtDesc(
            String userId);

    Optional<TaskExecution> findByTaskIdAndUserId(
            String taskId,
            String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select task
            from TaskExecution task
            where task.taskId = :taskId
            """)
    Optional<TaskExecution> findLockedByTaskId(
            @Param("taskId") String taskId);
}