package dev.careflow.api;

import dev.careflow.api.Contracts.*;
import dev.careflow.domain.Visit;
import dev.careflow.security.CenterContext;
import dev.careflow.service.CareService;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.YearMonth;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class CareController {
  private final CareService service;
  private final CenterContext context;
  private final Clock clock;

  public CareController(CareService service, CenterContext context, Clock clock) {
    this.service = service;
    this.context = context;
    this.clock = clock;
  }

  @GetMapping("/csrf")
  Map<String, String> csrf(CsrfToken csrf) {
    return Map.of("token", csrf.getToken(), "headerName", csrf.getHeaderName());
  }

  @GetMapping("/session")
  Map<String, Object> session(Authentication auth) {
    return Map.of(
        "username",
        auth.getName(),
        "centerId",
        context.centerId(),
        "centerName",
        context.centerId() == 1 ? "늘봄 주야간보호센터" : "다온 주야간보호센터",
        "canWrite",
        auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER")));
  }

  @GetMapping("/dashboard")
  Dashboard dashboard(@RequestParam(required = false) String month) {
    return service.dashboard(month == null ? YearMonth.now(clock).toString() : month);
  }

  @GetMapping("/recipients")
  PageResult<RecipientView> recipients(
      @RequestParam(defaultValue = "") String name,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.recipients(name, page, size);
  }

  @PostMapping("/recipients")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('MANAGER')")
  RecipientView createRecipient(@Valid @RequestBody RecipientInput input) {
    return service.createRecipient(input);
  }

  @GetMapping("/visits")
  PageResult<VisitView> visits(
      @RequestParam String month,
      @RequestParam(required = false) Visit.Status status,
      @RequestParam(defaultValue = "") String name,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.visits(month, status, name, page, size);
  }

  @PostMapping("/visits")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('MANAGER')")
  VisitView createVisit(@Valid @RequestBody VisitInput input) {
    return service.createVisit(input);
  }

  @PatchMapping("/visits/{id}")
  @PreAuthorize("hasRole('MANAGER')")
  VisitView transition(@PathVariable long id, @Valid @RequestBody VisitTransition input) {
    return service.transitionVisit(id, input.status());
  }

  @GetMapping("/invoices")
  PageResult<InvoiceView> invoices(
      @RequestParam String month,
      @RequestParam(defaultValue = "false") boolean unpaidOnly,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.invoices(month, unpaidOnly, page, size);
  }

  @GetMapping("/invoices/{id}")
  InvoiceDetail invoice(@PathVariable long id) {
    return service.invoice(id);
  }

  @PostMapping("/invoices")
  @PreAuthorize("hasRole('MANAGER')")
  ResponseEntity<MutationResult<InvoiceView>> issue(@Valid @RequestBody InvoiceInput input) {
    var result = service.issueInvoice(input);
    return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
        .body(result);
  }

  @PostMapping("/invoices/{id}/payments")
  @PreAuthorize("hasRole('MANAGER')")
  ResponseEntity<MutationResult<PaymentView>> pay(
      @PathVariable long id,
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      @Valid @RequestBody PaymentInput input) {
    var result = service.pay(id, key, input);
    return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
        .body(result);
  }

  @GetMapping("/audit-events")
  PageResult<AuditView> audit(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return service.audit(page, size);
  }
}
