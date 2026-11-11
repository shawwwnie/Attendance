package dev.careflow.repository;

import dev.careflow.domain.Visit;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VisitRepository extends JpaRepository<Visit, Long> {
  Optional<Visit> findByIdAndCenterId(long id, long centerId);

  List<Visit> findByCenterIdAndRecipientIdAndServiceDateBetweenOrderByServiceDateAsc(
      long centerId, long recipientId, LocalDate from, LocalDate to);

  long countByCenterIdAndServiceDateAndStatus(long centerId, LocalDate date, Visit.Status status);
}
