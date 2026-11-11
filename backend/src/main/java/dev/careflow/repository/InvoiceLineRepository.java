package dev.careflow.repository;

import dev.careflow.domain.InvoiceLine;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, Long> {
  List<InvoiceLine> findByInvoiceIdAndCenterIdOrderByServiceDateAsc(long invoiceId, long centerId);
}
