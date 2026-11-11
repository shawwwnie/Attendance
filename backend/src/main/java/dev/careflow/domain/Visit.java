package dev.careflow.domain;

import dev.careflow.common.BusinessException;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "visit")
public class Visit extends BaseEntity {
  public enum Status {
    PLANNED,
    COMPLETED,
    CANCELLED
  }

  @Column(nullable = false)
  private long recipientId;

  @Column(nullable = false)
  private LocalDate serviceDate;

  @Column(nullable = false)
  private LocalTime startTime;

  @Column(nullable = false)
  private LocalTime endTime;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Status status;

  protected Visit() {}

  public Visit(
      long centerId,
      long recipientId,
      LocalDate date,
      LocalTime start,
      LocalTime end,
      Instant now) {
    super(centerId, now);
    this.recipientId = recipientId;
    serviceDate = date;
    startTime = start;
    endTime = end;
    status = Status.PLANNED;
  }

  public boolean transition(Status next) {
    if (status == next) return false;
    if (status != Status.PLANNED || next == Status.PLANNED) {
      throw BusinessException.conflict("INVALID_VISIT_TRANSITION", "예정 상태에서만 완료 또는 취소할 수 있습니다.");
    }
    status = next;
    return true;
  }

  public long getRecipientId() {
    return recipientId;
  }

  public LocalDate getServiceDate() {
    return serviceDate;
  }

  public LocalTime getStartTime() {
    return startTime;
  }

  public LocalTime getEndTime() {
    return endTime;
  }

  public Status getStatus() {
    return status;
  }
}
