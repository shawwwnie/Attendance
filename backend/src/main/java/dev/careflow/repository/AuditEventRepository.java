package dev.careflow.repository;

import dev.careflow.domain.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
  Page<AuditEvent> findByCenterIdOrderByCreatedAtDescIdDesc(long centerId, Pageable pageable);
}
