package dev.careflow.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "payment")
public class Payment extends BaseEntity {
  @Column(nullable = false)
  private long invoiceId;

  @Column(nullable = false, length = 64)
  private String idempotencyKey;

  @Column(nullable = false)
  private long amount;

  @Column(nullable = false, length = 100)
  private String memo;

  protected Payment() {}

  public Payment(Invoice invoice, String key, long amount, String memo, Instant now) {
    super(invoice.getCenterId(), now);
    invoiceId = invoice.getId();
    idempotencyKey = key;
    this.amount = amount;
    this.memo = memo;
  }

  public boolean matches(long invoiceId, long amount, String memo) {
    return this.invoiceId == invoiceId && this.amount == amount && this.memo.equals(memo);
  }

  public long getInvoiceId() {
    return invoiceId;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public long getAmount() {
    return amount;
  }

  public String getMemo() {
    return memo;
  }
}
