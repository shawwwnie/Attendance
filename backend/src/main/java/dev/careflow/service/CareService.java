package dev.careflow.service;

import dev.careflow.api.Contracts.*;
import dev.careflow.common.BusinessException;
import dev.careflow.domain.*;
import dev.careflow.repository.*;
import dev.careflow.security.CenterContext;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CareService {
  private final RecipientRepository recipients;
  private final VisitRepository visits;
  private final InvoiceRepository invoices;
  private final InvoiceLineRepository lines;
  private final PaymentRepository payments;
  private final AuditEventRepository audit;
  private final CareQueries queries;
  private final CenterContext context;
  private final Clock clock;

  public CareService(
      RecipientRepository recipients,
      VisitRepository visits,
      InvoiceRepository invoices,
      InvoiceLineRepository lines,
      PaymentRepository payments,
      AuditEventRepository audit,
      CareQueries queries,
      CenterContext context,
      Clock clock) {
    this.recipients = recipients;
    this.visits = visits;
    this.invoices = invoices;
    this.lines = lines;
    this.payments = payments;
    this.audit = audit;
    this.queries = queries;
    this.context = context;
    this.clock = clock;
  }

  public PageResult<RecipientView> recipients(String name, int page, int size) {
    checkPage(page, size);
    var result =
        recipients.findByCenterIdAndNameContainingIgnoreCase(
            context.centerId(),
            name.strip(),
            PageRequest.of(page, size, Sort.by("id").descending()));
    return new PageResult<>(
        result.map(RecipientView::of).getContent(), result.getTotalElements(), page, size);
  }

  @Transactional
  @PreAuthorize("hasRole('MANAGER')")
  public RecipientView createRecipient(RecipientInput input) {
    Recipient recipient =
        recipients.save(
            new Recipient(
                context.centerId(),
                input.name().strip(),
                input.careGrade(),
                input.dailyFee(),
                input.copayBasisPoints(),
                clock.instant()));
    record("RECIPIENT_CREATED", "RECIPIENT", recipient.getId(), "수급자 등록");
    return RecipientView.of(recipient);
  }

  public PageResult<VisitView> visits(
      String month, Visit.Status status, String name, int page, int size) {
    checkPage(page, size);
    return queries.visits(context.centerId(), parseMonth(month), status, name, page, size);
  }

  @Transactional
  @PreAuthorize("hasRole('MANAGER')")
  public VisitView createVisit(VisitInput input) {
    Recipient recipient = lockRecipient(input.recipientId());
    if (!input.endTime().isAfter(input.startTime())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "INVALID_TIME_RANGE", "종료 시간은 시작 시간 이후여야 합니다.");
    }
    String serviceMonth = parseMonth(YearMonth.from(input.serviceDate()).toString()).toString();
    assertMonthOpen(recipient.getId(), serviceMonth);
    Visit visit =
        visits.saveAndFlush(
            new Visit(
                context.centerId(),
                recipient.getId(),
                input.serviceDate(),
                input.startTime(),
                input.endTime(),
                clock.instant()));
    record("VISIT_CREATED", "VISIT", visit.getId(), input.serviceDate() + " 일정 등록");
    return visitView(visit, recipient);
  }

  @Transactional
  @PreAuthorize("hasRole('MANAGER')")
  public VisitView transitionVisit(long id, Visit.Status status) {
    // All visit mutations and monthly closing acquire the same recipient lock first.
    Visit current =
        visits.findByIdAndCenterId(id, context.centerId()).orElseThrow(BusinessException::notFound);
    Recipient recipient = lockRecipient(current.getRecipientId());
    // Refresh after acquiring the lock: another transaction may have completed the visit.
    visits.flush();
    entityManager.refresh(current);
    if (current.getStatus() == status) return visitView(current, recipient);
    assertMonthOpen(recipient.getId(), YearMonth.from(current.getServiceDate()).toString());
    if (status == Visit.Status.COMPLETED
        && current.getServiceDate().isAfter(LocalDate.now(clock))) {
      throw BusinessException.conflict("FUTURE_VISIT", "미래 일정은 이용 완료할 수 없습니다.");
    }
    if (current.transition(status))
      record("VISIT_" + status, "VISIT", id, current.getServiceDate() + " 상태 변경");
    return visitView(current, recipient);
  }

  @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

  public PageResult<InvoiceView> invoices(String month, boolean unpaidOnly, int page, int size) {
    checkPage(page, size);
    return queries.invoices(
        context.centerId(), parseMonth(month).toString(), unpaidOnly, page, size);
  }

  @Transactional
  @PreAuthorize("hasRole('MANAGER')")
  public MutationResult<InvoiceView> issueInvoice(InvoiceInput input) {
    YearMonth month = parseMonth(input.month());
    Recipient recipient = lockRecipient(input.recipientId());
    var existing =
        invoices.findByCenterIdAndRecipientIdAndBillingMonth(
            context.centerId(), recipient.getId(), month.toString());
    if (existing.isPresent())
      return new MutationResult<>(InvoiceView.of(existing.get(), recipient.getName()), true);
    List<Visit> monthly =
        visits.findByCenterIdAndRecipientIdAndServiceDateBetweenOrderByServiceDateAsc(
            context.centerId(), recipient.getId(), month.atDay(1), month.atEndOfMonth());
    if (monthly.stream().anyMatch(v -> v.getStatus() == Visit.Status.PLANNED)) {
      throw BusinessException.conflict("PENDING_VISITS", "예정 일정을 완료 또는 취소한 뒤 청구서를 발행해주세요.");
    }
    List<Visit> completed =
        monthly.stream().filter(v -> v.getStatus() == Visit.Status.COMPLETED).toList();
    if (completed.isEmpty())
      throw BusinessException.conflict("NO_COMPLETED_VISITS", "해당 월에 완료된 이용 일정이 없습니다.");
    long total = Math.multiplyExact(recipient.getDailyFee(), completed.size());
    long copay =
        Math.multiplyExact(
            BillingPolicy.copay(recipient.getDailyFee(), recipient.getCopayBasisPoints()),
            completed.size());
    Invoice invoice =
        invoices.saveAndFlush(
            new Invoice(
                context.centerId(),
                recipient.getId(),
                month.toString(),
                total,
                copay,
                clock.instant()));
    lines.saveAll(
        completed.stream()
            .map(v -> new InvoiceLine(invoice, v, recipient, clock.instant()))
            .toList());
    record(
        "INVOICE_ISSUED",
        "INVOICE",
        invoice.getId(),
        month + " / " + completed.size() + "일 / 본인부담금 " + copay + "원");
    return new MutationResult<>(InvoiceView.of(invoice, recipient.getName()), false);
  }

  public InvoiceDetail invoice(long id) {
    Invoice invoice =
        invoices
            .findByIdAndCenterId(id, context.centerId())
            .orElseThrow(BusinessException::notFound);
    Recipient recipient =
        recipients
            .findByIdAndCenterId(invoice.getRecipientId(), context.centerId())
            .orElseThrow(BusinessException::notFound);
    return new InvoiceDetail(
        InvoiceView.of(invoice, recipient.getName()),
        lines.findByInvoiceIdAndCenterIdOrderByServiceDateAsc(id, context.centerId()).stream()
            .map(LineView::of)
            .toList(),
        payments.findByInvoiceIdAndCenterIdOrderByCreatedAtDesc(id, context.centerId()).stream()
            .map(PaymentView::of)
            .toList());
  }

  @Transactional
  @PreAuthorize("hasRole('MANAGER')")
  public MutationResult<PaymentView> pay(long id, String key, PaymentInput input) {
    if (key == null || !key.matches("[A-Za-z0-9_-]{8,64}")) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", "8~64자의 Idempotency-Key가 필요합니다.");
    }
    Invoice invoice =
        invoices
            .findLockedByIdAndCenterId(id, context.centerId())
            .orElseThrow(BusinessException::notFound);
    String memo = input.memo().strip();
    var existing = payments.findByCenterIdAndIdempotencyKey(context.centerId(), key);
    if (existing.isPresent()) {
      Payment payment = existing.get();
      if (!payment.matches(id, input.amount(), memo)) {
        throw BusinessException.conflict("IDEMPOTENCY_CONFLICT", "같은 요청 키에 다른 수납 내용이 전달되었습니다.");
      }
      return new MutationResult<>(PaymentView.of(payment), true);
    }
    invoice.pay(input.amount());
    Payment payment =
        payments.saveAndFlush(new Payment(invoice, key, input.amount(), memo, clock.instant()));
    record(
        "PAYMENT_RECEIVED",
        "PAYMENT",
        payment.getId(),
        "청구서 #" + id + " / " + input.amount() + "원 수납");
    return new MutationResult<>(PaymentView.of(payment), false);
  }

  public PageResult<AuditView> audit(int page, int size) {
    checkPage(page, size);
    var result =
        audit.findByCenterIdOrderByCreatedAtDescIdDesc(
            context.centerId(), PageRequest.of(page, size));
    return new PageResult<>(
        result.map(AuditView::of).getContent(), result.getTotalElements(), page, size);
  }

  public Dashboard dashboard(String month) {
    String normalized = parseMonth(month).toString();
    LocalDate today = LocalDate.now(clock);
    long center = context.centerId();
    return new Dashboard(
        today,
        normalized,
        recipients.countByCenterId(center),
        visits.countByCenterIdAndServiceDateAndStatus(center, today, Visit.Status.PLANNED),
        visits.countByCenterIdAndServiceDateAndStatus(center, today, Visit.Status.COMPLETED),
        queries.billingSummary(center, normalized),
        audit(0, 6).items());
  }

  private Recipient lockRecipient(long id) {
    return recipients
        .findLockedByIdAndCenterId(id, context.centerId())
        .orElseThrow(BusinessException::notFound);
  }

  private void assertMonthOpen(long recipientId, String month) {
    if (invoices
        .findByCenterIdAndRecipientIdAndBillingMonth(context.centerId(), recipientId, month)
        .isPresent()) {
      throw BusinessException.conflict("MONTH_CLOSED", "이미 청구된 월의 일정은 변경할 수 없습니다.");
    }
  }

  private void record(String action, String type, long id, String detail) {
    audit.save(
        new AuditEvent(
            context.centerId(), context.actor(), action, type, id, detail, clock.instant()));
  }

  private VisitView visitView(Visit visit, Recipient recipient) {
    return new VisitView(
        visit.getId(),
        recipient.getId(),
        recipient.getName(),
        visit.getServiceDate(),
        visit.getStartTime(),
        visit.getEndTime(),
        visit.getStatus());
  }

  public static YearMonth parseMonth(String value) {
    if (value == null || !value.matches("\\d{4}-(0[1-9]|1[0-2])"))
      throw new IllegalArgumentException("Invalid month");
    YearMonth month = YearMonth.parse(value);
    if (month.getYear() < 2000 || month.getYear() > 2100)
      throw new IllegalArgumentException("Invalid year");
    return month;
  }

  private static void checkPage(int page, int size) {
    if (page < 0 || page > 10000 || size < 1 || size > 100)
      throw new IllegalArgumentException("Invalid pagination");
  }
}
