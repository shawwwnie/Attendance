package dev.careflow.domain;

import dev.careflow.common.BusinessException;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "invoice")
public class Invoice extends BaseEntity {
  @Column(nullable = false)
  private long recipientId;

  @Column(nullable = false, length = 7)
  private String billingMonth;

  @Column(nullable = false)
  private long totalAmount;

  @Column(nullable = false)
  private long copayAmount;

  @Column(nullable = false)
  private long paidAmount;

  protected Invoice() {}

  public Invoice(
      long centerId, long recipientId, String month, long total, long copay, Instant now) {
    super(centerId, now);
    this.recipientId = recipientId;
    billingMonth = month;
    totalAmount = total;
    copayAmount = copay;
  }

  public void pay(long amount) {
    if (amount <= 0 || amount > getOutstandingAmount()) {
      throw BusinessException.conflict("OVERPAYMENT", "수납액은 0원보다 크고 미수금 이하여야 합니다.");
    }
    paidAmount += amount;
  }

  public long getRecipientId() {
    return recipientId;
  }

  public String getBillingMonth() {
    return billingMonth;
  }

  public long getTotalAmount() {
    return totalAmount;
  }

  public long getCopayAmount() {
    return copayAmount;
  }

  public long getPaidAmount() {
    return paidAmount;
  }

  public long getOutstandingAmount() {
    return copayAmount - paidAmount;
  }
}
