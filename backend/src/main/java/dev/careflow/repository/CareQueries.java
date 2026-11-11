package dev.careflow.repository;

import static dev.careflow.domain.QInvoice.invoice;
import static dev.careflow.domain.QRecipient.recipient;
import static dev.careflow.domain.QVisit.visit;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.careflow.api.Contracts.*;
import dev.careflow.domain.Visit;
import jakarta.persistence.EntityManager;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Read projections: center scoping is mandatory; no entity serialization or per-row lookups. */
@Repository
public class CareQueries {
  private final JPAQueryFactory query;

  public CareQueries(EntityManager em) {
    query = new JPAQueryFactory(em);
  }

  public PageResult<VisitView> visits(
      long centerId, YearMonth month, Visit.Status status, String name, int page, int size) {
    BooleanBuilder where =
        new BooleanBuilder(visit.centerId.eq(centerId))
            .and(visit.serviceDate.between(month.atDay(1), month.atEndOfMonth()));
    if (status != null) where.and(visit.status.eq(status));
    if (name != null && !name.isBlank()) where.and(recipient.name.containsIgnoreCase(name.strip()));
    List<VisitView> items =
        query
            .select(
                Projections.constructor(
                    VisitView.class,
                    visit.id,
                    visit.recipientId,
                    recipient.name,
                    visit.serviceDate,
                    visit.startTime,
                    visit.endTime,
                    visit.status))
            .from(visit)
            .join(recipient)
            .on(recipient.id.eq(visit.recipientId).and(recipient.centerId.eq(centerId)))
            .where(where)
            .orderBy(visit.serviceDate.desc(), visit.startTime.asc(), visit.id.desc())
            .offset((long) page * size)
            .limit(size)
            .fetch();
    Long total =
        query
            .select(visit.count())
            .from(visit)
            .join(recipient)
            .on(recipient.id.eq(visit.recipientId).and(recipient.centerId.eq(centerId)))
            .where(where)
            .fetchOne();
    return new PageResult<>(items, total == null ? 0 : total, page, size);
  }

  public PageResult<InvoiceView> invoices(
      long centerId, String month, boolean unpaidOnly, int page, int size) {
    BooleanBuilder where =
        new BooleanBuilder(invoice.centerId.eq(centerId)).and(invoice.billingMonth.eq(month));
    if (unpaidOnly) where.and(invoice.paidAmount.lt(invoice.copayAmount));
    List<InvoiceView> items =
        query
            .select(
                Projections.constructor(
                    InvoiceView.class,
                    invoice.id,
                    invoice.recipientId,
                    recipient.name,
                    invoice.billingMonth,
                    invoice.totalAmount,
                    invoice.copayAmount,
                    invoice.paidAmount,
                    invoice.copayAmount.subtract(invoice.paidAmount)))
            .from(invoice)
            .join(recipient)
            .on(recipient.id.eq(invoice.recipientId).and(recipient.centerId.eq(centerId)))
            .where(where)
            .orderBy(invoice.id.desc())
            .offset((long) page * size)
            .limit(size)
            .fetch();
    Long total = query.select(invoice.count()).from(invoice).where(where).fetchOne();
    return new PageResult<>(items, total == null ? 0 : total, page, size);
  }

  public BillingSummary billingSummary(long centerId, String month) {
    var result =
        query
            .select(
                invoice.count(),
                invoice.copayAmount.sum().coalesce(0L),
                invoice.paidAmount.sum().coalesce(0L))
            .from(invoice)
            .where(invoice.centerId.eq(centerId), invoice.billingMonth.eq(month))
            .fetchOne();
    if (result == null) return new BillingSummary(0, 0, 0, 0);
    long count = result.get(0, Long.class),
        copay = result.get(1, Long.class),
        paid = result.get(2, Long.class);
    return new BillingSummary(count, copay, paid, copay - paid);
  }
}
