package dev.careflow.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "recipient")
public class Recipient extends BaseEntity {
  @Column(nullable = false, length = 40)
  private String name;

  @Column(nullable = false)
  private int careGrade;

  @Column(nullable = false)
  private long dailyFee;

  @Column(nullable = false)
  private int copayBasisPoints;

  protected Recipient() {}

  public Recipient(
      long centerId, String name, int careGrade, long dailyFee, int copayBasisPoints, Instant now) {
    super(centerId, now);
    this.name = name;
    this.careGrade = careGrade;
    this.dailyFee = dailyFee;
    this.copayBasisPoints = copayBasisPoints;
  }

  public String getName() {
    return name;
  }

  public int getCareGrade() {
    return careGrade;
  }

  public long getDailyFee() {
    return dailyFee;
  }

  public int getCopayBasisPoints() {
    return copayBasisPoints;
  }
}
