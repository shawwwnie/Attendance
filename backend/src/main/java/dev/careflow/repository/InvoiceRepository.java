package dev.careflow.repository;

import dev.careflow.domain.Invoice;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
  Optional<Invoice> findByCenterIdAndRecipientIdAndBillingMonth(
      long centerId, long recipientId, String month);

  Optional<Invoice> findByIdAndCenterId(long id, long centerId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Invoice> findLockedByIdAndCenterId(long id, long centerId);
}
