package dev.careflow.repository;

import dev.careflow.domain.Recipient;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface RecipientRepository extends JpaRepository<Recipient, Long> {
  Page<Recipient> findByCenterIdAndNameContainingIgnoreCase(
      long centerId, String name, Pageable page);

  Optional<Recipient> findByIdAndCenterId(long id, long centerId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Recipient> findLockedByIdAndCenterId(long id, long centerId);

  long countByCenterId(long centerId);
}
