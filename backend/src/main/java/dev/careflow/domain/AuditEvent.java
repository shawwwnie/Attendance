package dev.careflow.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_event")
public class AuditEvent extends BaseEntity {
  @Column(nullable = false, length = 40)
  private String actor;

  @Column(nullable = false, length = 40)
  private String action;

  @Column(nullable = false, length = 40)
  private String entityType;

  @Column(nullable = false)
  private long entityId;

  @Column(nullable = false, length = 250)
  private String detail;

  protected AuditEvent() {}

  public AuditEvent(
      long centerId,
      String actor,
      String action,
      String type,
      long id,
      String detail,
      Instant now) {
    super(centerId, now);
    this.actor = actor;
    this.action = action;
    entityType = type;
    entityId = id;
    this.detail = detail;
  }

  public String getActor() {
    return actor;
  }

  public String getAction() {
    return action;
  }

  public String getEntityType() {
    return entityType;
  }

  public long getEntityId() {
    return entityId;
  }

  public String getDetail() {
    return detail;
  }
}
