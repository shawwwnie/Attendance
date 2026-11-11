package dev.careflow.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "invoice_line")
public class InvoiceLine extends BaseEntity {
  @Column(nullable = false)
  private long invoiceId;

  @Column(nullable = false)
  private long visitId;

  @Column(nullable = false)
  private LocalDate serviceDate;

  @Column(nullable = false)
  private long dailyFee;

  @Column(nullable = false)
  private int copayBasisPoints;

  @Column(nullable = false)
  private long copayAmount;

  protected InvoiceLine() {}

  public InvoiceLine(Invoice invoice, Visit visit, Recipient recipient, Instant now) {
    super(invoice.getCenterId(), now);
    invoiceId = invoice.getId();
    visitId = visit.getId();
    serviceDate = visit.getServiceDate();
    dailyFee = recipient.getDailyFee();
    copayBasisPoints = recipient.getCopayBasisPoints();
    copayAmount = BillingPolicy.copay(dailyFee, copayBasisPoints);
  }

  public long getInvoiceId() {
    return invoiceId;
  }

  public long getVisitId() {
    return visitId;
  }

  public LocalDate getServiceDate() {
    return serviceDate;
  }

  public long getDailyFee() {
    return dailyFee;
  }

  public int getCopayBasisPoints() {
    return copayBasisPoints;
  }

  public long getCopayAmount() {
    return copayAmount;
  }
}
