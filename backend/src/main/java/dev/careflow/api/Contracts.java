package dev.careflow.api;

import dev.careflow.domain.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class Contracts {
  private Contracts() {}

  public record PageResult<T>(List<T> items, long total, int page, int size) {}

  public record RecipientInput(
      @NotBlank @Size(max = 40) String name,
      @Min(1) @Max(5) int careGrade,
      @Min(1000) @Max(1000000) long dailyFee,
      @Min(0) @Max(10000) int copayBasisPoints) {}

  public record RecipientView(
      long id, String name, int careGrade, long dailyFee, int copayBasisPoints) {
    public static RecipientView of(Recipient r) {
      return new RecipientView(
          r.getId(), r.getName(), r.getCareGrade(), r.getDailyFee(), r.getCopayBasisPoints());
    }
  }

  public record VisitInput(
      @Positive long recipientId,
      @NotNull LocalDate serviceDate,
      @NotNull LocalTime startTime,
      @NotNull LocalTime endTime) {}

  public record VisitTransition(@NotNull Visit.Status status) {}

  public record VisitView(
      long id,
      long recipientId,
      String recipientName,
      LocalDate serviceDate,
      LocalTime startTime,
      LocalTime endTime,
      Visit.Status status) {}

  public record InvoiceInput(
      @Positive long recipientId,
      @NotBlank @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])") String month) {}

  public record InvoiceView(
      long id,
      long recipientId,
      String recipientName,
      String billingMonth,
      long totalAmount,
      long copayAmount,
      long paidAmount,
      long outstandingAmount) {
    public static InvoiceView of(Invoice i, String name) {
      return new InvoiceView(
          i.getId(),
          i.getRecipientId(),
          name,
          i.getBillingMonth(),
          i.getTotalAmount(),
          i.getCopayAmount(),
          i.getPaidAmount(),
          i.getOutstandingAmount());
    }
  }

  public record LineView(
      long visitId, LocalDate serviceDate, long dailyFee, int copayBasisPoints, long copayAmount) {
    public static LineView of(InvoiceLine l) {
      return new LineView(
          l.getVisitId(),
          l.getServiceDate(),
          l.getDailyFee(),
          l.getCopayBasisPoints(),
          l.getCopayAmount());
    }
  }

  public record PaymentInput(
      @Min(1) @Max(1000000000) long amount, @NotNull @Size(max = 100) String memo) {}

  public record PaymentView(long id, long amount, String memo, Instant createdAt) {
    public static PaymentView of(Payment p) {
      return new PaymentView(p.getId(), p.getAmount(), p.getMemo(), p.getCreatedAt());
    }
  }

  public record InvoiceDetail(
      InvoiceView invoice, List<LineView> lines, List<PaymentView> payments) {}

  public record MutationResult<T>(T data, boolean replayed) {}

  public record AuditView(
      long id,
      String actor,
      String action,
      String entityType,
      long entityId,
      String detail,
      Instant createdAt) {
    public static AuditView of(AuditEvent a) {
      return new AuditView(
          a.getId(),
          a.getActor(),
          a.getAction(),
          a.getEntityType(),
          a.getEntityId(),
          a.getDetail(),
          a.getCreatedAt());
    }
  }

  public record BillingSummary(
      long invoiceCount, long copayAmount, long paidAmount, long outstandingAmount) {}

  public record Dashboard(
      LocalDate today,
      String month,
      long recipients,
      long plannedToday,
      long completedToday,
      BillingSummary billing,
      List<AuditView> recentActivity) {}
}
