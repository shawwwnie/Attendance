package dev.careflow.repository;

import dev.careflow.domain.Payment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
  Optional<Payment> findByCenterIdAndIdempotencyKey(long centerId, String key);

  List<Payment> findByInvoiceIdAndCenterIdOrderByCreatedAtDesc(long invoiceId, long centerId);
}
