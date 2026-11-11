package dev.careflow.config;

import dev.careflow.domain.*;
import dev.careflow.repository.*;
import java.time.*;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "careflow.demo-enabled", havingValue = "true")
public class DemoSeeder implements ApplicationRunner {
  private final RecipientRepository recipients;
  private final VisitRepository visits;
  private final InvoiceRepository invoices;
  private final InvoiceLineRepository lines;
  private final PaymentRepository payments;
  private final AuditEventRepository audit;
  private final Clock clock;

  public DemoSeeder(
      RecipientRepository recipients,
      VisitRepository visits,
      InvoiceRepository invoices,
      InvoiceLineRepository lines,
      PaymentRepository payments,
      AuditEventRepository audit,
      Clock clock) {
    this.recipients = recipients;
    this.visits = visits;
    this.invoices = invoices;
    this.lines = lines;
    this.payments = payments;
    this.audit = audit;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (recipients.count() > 0) return;
    Instant now = clock.instant();
    LocalDate today = LocalDate.now(clock);
    LocalDate first = today.withDayOfMonth(1);
    List<Recipient> list =
        recipients.saveAll(
            List.of(
                new Recipient(1, "김하늘", 3, 60000, 1500, now),
                new Recipient(1, "박다온", 4, 55000, 900, now),
                new Recipient(1, "이서준", 2, 65000, 1500, now),
                new Recipient(1, "최가온", 5, 50000, 600, now),
                new Recipient(1, "한여름", 3, 60000, 0, now),
                new Recipient(1, "정이든", 4, 55000, 1500, now),
                new Recipient(2, "타센터 샘플", 3, 60000, 1500, now)));
    for (int n = 0; n < 6; n++) {
      Recipient recipient = list.get(n);
      for (int day = 0; day < Math.min(4, today.getDayOfMonth() - 1); day++) {
        Visit visit =
            new Visit(
                1,
                recipient.getId(),
                first.plusDays(day),
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                now);
        visit.transition(Visit.Status.COMPLETED);
        visits.save(visit);
      }
      if (n == 0 || n == 2 || n == 3)
        visits.save(
            new Visit(1, recipient.getId(), today, LocalTime.of(9, 0), LocalTime.of(17, 0), now));
    }
    // One partially paid invoice gives the dashboard a useful initial state.
    Recipient recipient = list.get(1);
    List<Visit> completed =
        visits.findByCenterIdAndRecipientIdAndServiceDateBetweenOrderByServiceDateAsc(
            1, recipient.getId(), first, today);
    if (!completed.isEmpty()) {
      long copay =
          BillingPolicy.copay(recipient.getDailyFee(), recipient.getCopayBasisPoints())
              * completed.size();
      Invoice invoice =
          invoices.saveAndFlush(
              new Invoice(
                  1,
                  recipient.getId(),
                  YearMonth.from(today).toString(),
                  recipient.getDailyFee() * completed.size(),
                  copay,
                  now));
      lines.saveAll(
          completed.stream().map(v -> new InvoiceLine(invoice, v, recipient, now)).toList());
      long amount = Math.min(5000, copay);
      invoice.pay(amount);
      payments.save(new Payment(invoice, "seed-payment-001", amount, "학습용 샘플 수납", now));
    }
    audit.save(
        new AuditEvent(1, "SYSTEM", "DEMO_INITIALIZED", "CENTER", 1, "가상 수급자·일정·청구 데이터 준비", now));
  }
}
