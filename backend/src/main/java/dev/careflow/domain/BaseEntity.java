package dev.careflow.domain;

import jakarta.persistence.*;
import java.time.Instant;

@MappedSuperclass
public abstract class BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  protected Long id;

  @Column(nullable = false)
  protected long centerId;

  @Version protected long version;

  @Column(nullable = false, updatable = false)
  protected Instant createdAt;

  protected BaseEntity() {}

  protected BaseEntity(long centerId, Instant now) {
    this.centerId = centerId;
    this.createdAt = now;
  }

  public Long getId() {
    return id;
  }

  public long getCenterId() {
    return centerId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
