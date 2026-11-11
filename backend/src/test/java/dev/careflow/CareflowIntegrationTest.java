package dev.careflow;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Real PostgreSQL, Flyway, Spring Security and independent concurrent HTTP transactions. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class CareflowIntegrationTest {
  static final EmbeddedPostgres POSTGRES;

  static {
    try {
      POSTGRES =
          EmbeddedPostgres.builder()
              .setPort(0)
              .setServerConfig("unix_socket_directories", "")
              .start();
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
    registry.add("spring.datasource.username", () -> "postgres");
    registry.add("spring.datasource.password", () -> "");
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  final LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
  final String month = YearMonth.from(today).toString();

  @BeforeEach
  void clean() {
    jdbc.execute(
        "TRUNCATE TABLE audit_event, payment, invoice_line, invoice, visit, recipient RESTART"
            + " IDENTITY CASCADE");
  }

  @Test
  void protectsAuthenticationCsrfAndReadOnlyUsers() throws Exception {
    mvc.perform(get("/api/dashboard"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().exists("X-Request-ID"))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    mvc.perform(
            post("/api/recipients")
                .with(user("manager").roles("MANAGER"))
                .contentType("application/json")
                .content(recipientBody("샘플")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/recipients")
                .with(user("viewer").roles("VIEWER"))
                .with(csrf())
                .contentType("application/json")
                .content(recipientBody("샘플")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/recipients").with(user("viewer").roles("VIEWER")))
        .andExpect(status().isOk());
    mvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void validatesInputAndReturnsTraceableErrors() throws Exception {
    MvcResult result =
        send(
            post("/api/recipients"),
            "{\"name\":\"\",\"careGrade\":0,\"dailyFee\":0,\"copayBasisPoints\":10001}",
            "manager");
    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    JsonNode body = body(result);
    assertThat(body.path("code").asText()).isEqualTo("VALIDATION_ERROR");
    assertThat(body.path("fieldErrors").has("name")).isTrue();
    assertThat(body.path("requestId").asText())
        .isEqualTo(result.getResponse().getHeader("X-Request-ID"));
    mvc.perform(get("/api/visits").param("month", "2026-13").with(user("manager").roles("MANAGER")))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/recipients").param("size", "101").with(user("manager").roles("MANAGER")))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/visits").with(user("manager").roles("MANAGER")))
        .andExpect(status().isBadRequest());
    MvcResult tenantSpoof =
        send(
            post("/api/recipients"),
            recipientBody("샘플").replace("}", ",\"centerId\":2}"),
            "manager");
    assertThat(tenantSpoof.getResponse().getStatus()).isEqualTo(400);
  }

  @Test
  void completesVisitsIssuesInvoiceAndSnapshotsPerDayPrices() throws Exception {
    long person = createRecipient("김샘플", "manager");
    long visit = createVisit(person, today, "manager");
    complete(visit, "manager");
    MvcResult issued = issue(person, "manager");
    assertThat(issued.getResponse().getStatus()).isEqualTo(201);
    long invoice = body(issued).path("data").path("id").asLong();
    assertThat(body(issued).at("/data/copayAmount").asLong()).isEqualTo(9000);
    MvcResult replay = issue(person, "manager");
    assertThat(replay.getResponse().getStatus()).isEqualTo(200);
    assertThat(body(replay).path("replayed").asBoolean()).isTrue();
    assertThat(count("invoice")).isEqualTo(1);
    assertThat(count("invoice_line")).isEqualTo(1);
    jdbc.update(
        "UPDATE recipient SET daily_fee = 70000, copay_basis_points = 900 WHERE id = ?", person);
    mvc.perform(get("/api/invoices/{id}", invoice).with(user("manager").roles("MANAGER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lines[0].dailyFee").value(60000))
        .andExpect(jsonPath("$.lines[0].copayBasisPoints").value(1500));
    assertThat(
            send(post("/api/visits"), visitBody(person, today.withDayOfMonth(1)), "manager")
                .getResponse()
                .getStatus())
        .isEqualTo(409);
  }

  @Test
  void rejectsPendingBillingDuplicateDaysAndInvalidVisitTransitions() throws Exception {
    long person = createRecipient("김샘플", "manager");
    long visit = createVisit(person, today, "manager");
    assertThat(body(issue(person, "manager")).path("code").asText()).isEqualTo("PENDING_VISITS");
    MvcResult duplicate = send(post("/api/visits"), visitBody(person, today), "manager");
    assertThat(duplicate.getResponse().getStatus()).isEqualTo(409);
    assertThat(count("visit")).isEqualTo(1);
    assertThat(
            send(
                    post("/api/visits"),
                    visitBody(person, today.plusDays(1)).replace("17:00", "08:00"),
                    "manager")
                .getResponse()
                .getStatus())
        .isEqualTo(400);
    complete(visit, "manager");
    complete(visit, "manager"); // Replaying completion does not append an extra audit entry.
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE action = 'VISIT_COMPLETED'", Long.class))
        .isEqualTo(1);
    MvcResult cancelled =
        send(patch("/api/visits/" + visit), "{\"status\":\"CANCELLED\"}", "manager");
    assertThat(body(cancelled).path("code").asText()).isEqualTo("INVALID_VISIT_TRANSITION");
    long future = createVisit(person, today.plusDays(1), "manager");
    MvcResult early = send(patch("/api/visits/" + future), "{\"status\":\"COMPLETED\"}", "manager");
    assertThat(body(early).path("code").asText()).isEqualTo("FUTURE_VISIT");
  }

  @Test
  void scopesListsDetailsAndMutationsToTheAuthenticatedCenter() throws Exception {
    long otherPerson = createRecipient("다른 센터", "other-manager");
    long visit = createVisit(otherPerson, today, "other-manager");
    complete(visit, "other-manager");
    long invoice = body(issue(otherPerson, "other-manager")).at("/data/id").asLong();
    mvc.perform(get("/api/recipients").with(user("manager").roles("MANAGER")))
        .andExpect(jsonPath("$.total").value(0));
    mvc.perform(get("/api/visits").param("month", month).with(user("manager").roles("MANAGER")))
        .andExpect(jsonPath("$.total").value(0));
    mvc.perform(get("/api/invoices/{id}", invoice).with(user("manager").roles("MANAGER")))
        .andExpect(status().isNotFound());
    assertThat(
            send(post("/api/visits"), visitBody(otherPerson, today), "manager")
                .getResponse()
                .getStatus())
        .isEqualTo(404);
    assertThat(payment(invoice, "cross-center-key", 1000, "manager").getResponse().getStatus())
        .isEqualTo(404);
    assertThat(
            send(patch("/api/visits/" + visit), "{\"status\":\"CANCELLED\"}", "manager")
                .getResponse()
                .getStatus())
        .isEqualTo(404);
    mvc.perform(get("/api/audit-events").with(user("manager").roles("MANAGER")))
        .andExpect(jsonPath("$.total").value(0));
  }

  @Test
  void replaysPaymentsAndRejectsKeyReuseOrOverpaymentWithoutPartialWrites() throws Exception {
    long invoice = readyInvoice();
    MvcResult first = payment(invoice, "payment-request-001", 3000, "manager");
    assertThat(first.getResponse().getStatus()).isEqualTo(201);
    MvcResult replay = payment(invoice, "payment-request-001", 3000, "manager");
    assertThat(replay.getResponse().getStatus()).isEqualTo(200);
    assertThat(body(replay).at("/data/id").asLong()).isEqualTo(body(first).at("/data/id").asLong());
    assertThat(body(payment(invoice, "payment-request-001", 3001, "manager")).path("code").asText())
        .isEqualTo("IDEMPOTENCY_CONFLICT");
    assertThat(body(payment(invoice, "payment-request-002", 6001, "manager")).path("code").asText())
        .isEqualTo("OVERPAYMENT");
    assertThat(count("payment")).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT paid_amount FROM invoice WHERE id = ?", Long.class, invoice))
        .isEqualTo(3000);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE action = 'PAYMENT_RECEIVED'", Long.class))
        .isEqualTo(1);
    assertThat(payment(invoice, "payment-request-003", 6000, "manager").getResponse().getStatus())
        .isEqualTo(201);
    mvc.perform(get("/api/dashboard").param("month", month).with(user("manager").roles("MANAGER")))
        .andExpect(jsonPath("$.billing.outstandingAmount").value(0))
        .andExpect(jsonPath("$.billing.paidAmount").value(9000));
  }

  @Test
  void querydslFiltersAndPaginationReturnConsistentCounts() throws Exception {
    long first = createRecipient("김검색", "manager");
    long second = createRecipient("박검색", "manager");
    complete(createVisit(first, today, "manager"), "manager");
    createVisit(second, today, "manager");
    mvc.perform(
            get("/api/visits")
                .param("month", month)
                .param("name", "김")
                .param("status", "COMPLETED")
                .param("size", "1")
                .with(user("manager").roles("MANAGER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].recipientName").value("김검색"));
    mvc.perform(
            get("/api/visits")
                .param("month", month)
                .param("size", "1")
                .param("page", "1")
                .with(user("manager").roles("MANAGER")))
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.items.length()").value(1));
  }

  @Test
  void concurrentInvoiceRequestsCreateExactlyOneInvoice() throws Exception {
    long person = createRecipient("동시 청구", "manager");
    complete(createVisit(person, today, "manager"), "manager");
    List<Integer> statuses =
        concurrently(
            () -> issue(person, "manager").getResponse().getStatus(),
            () -> issue(person, "manager").getResponse().getStatus());
    assertThat(statuses).containsExactlyInAnyOrder(201, 200);
    assertThat(count("invoice")).isEqualTo(1);
    assertThat(count("invoice_line")).isEqualTo(1);
  }

  @Test
  void concurrentDistinctPaymentsCannotExceedOutstandingBalance() throws Exception {
    long invoice = readyInvoice();
    List<Integer> statuses =
        concurrently(
            () ->
                payment(invoice, "parallel-payment-001", 6000, "manager").getResponse().getStatus(),
            () ->
                payment(invoice, "parallel-payment-002", 6000, "manager")
                    .getResponse()
                    .getStatus());
    assertThat(statuses).containsExactlyInAnyOrder(201, 409);
    assertThat(count("payment")).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT paid_amount FROM invoice WHERE id = ?", Long.class, invoice))
        .isEqualTo(6000);
  }

  @Test
  void concurrentIdenticalPaymentRetriesReturnTheSameReceipt() throws Exception {
    long invoice = readyInvoice();
    List<Integer> statuses =
        concurrently(
            () -> payment(invoice, "same-request-key", 4000, "manager").getResponse().getStatus(),
            () -> payment(invoice, "same-request-key", 4000, "manager").getResponse().getStatus());
    assertThat(statuses).containsExactlyInAnyOrder(201, 200);
    assertThat(count("payment")).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM payment", Long.class)).isEqualTo(4000);
  }

  private long readyInvoice() throws Exception {
    long person = createRecipient("수납 샘플", "manager");
    complete(createVisit(person, today, "manager"), "manager");
    return body(issue(person, "manager")).at("/data/id").asLong();
  }

  private long createRecipient(String name, String actor) throws Exception {
    MvcResult result = send(post("/api/recipients"), recipientBody(name), actor);
    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    return body(result).path("id").asLong();
  }

  private long createVisit(long person, LocalDate date, String actor) throws Exception {
    MvcResult result = send(post("/api/visits"), visitBody(person, date), actor);
    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    return body(result).path("id").asLong();
  }

  private void complete(long visit, String actor) throws Exception {
    assertThat(
            send(patch("/api/visits/" + visit), "{\"status\":\"COMPLETED\"}", actor)
                .getResponse()
                .getStatus())
        .isEqualTo(200);
  }

  private MvcResult issue(long person, String actor) throws Exception {
    return send(
        post("/api/invoices"),
        json.writeValueAsString(Map.of("recipientId", person, "month", month)),
        actor);
  }

  private MvcResult payment(long invoice, String key, int amount, String actor) throws Exception {
    return send(
        post("/api/invoices/" + invoice + "/payments").header("Idempotency-Key", key),
        json.writeValueAsString(Map.of("amount", amount, "memo", "입금 확인")),
        actor);
  }

  private MvcResult send(MockHttpServletRequestBuilder request, String body, String actor)
      throws Exception {
    return mvc.perform(
            request
                .with(user(actor).roles("MANAGER"))
                .with(csrf())
                .contentType("application/json")
                .content(body))
        .andReturn();
  }

  private JsonNode body(MvcResult result) throws Exception {
    return json.readTree(
        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
  }

  private long count(String table) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
  }

  private String recipientBody(String name) throws Exception {
    return json.writeValueAsString(
        Map.of("name", name, "careGrade", 3, "dailyFee", 60000, "copayBasisPoints", 1500));
  }

  private String visitBody(long person, LocalDate date) throws Exception {
    return json.writeValueAsString(
        Map.of(
            "recipientId",
            person,
            "serviceDate",
            date.toString(),
            "startTime",
            "09:00",
            "endTime",
            "17:00"));
  }

  @SafeVarargs
  private final <T> List<T> concurrently(Callable<T>... tasks) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(tasks.length);
    CountDownLatch ready = new CountDownLatch(tasks.length), go = new CountDownLatch(1);
    try {
      List<Future<T>> results = new ArrayList<>();
      for (Callable<T> task : tasks)
        results.add(
            pool.submit(
                () -> {
                  ready.countDown();
                  if (!go.await(10, TimeUnit.SECONDS)) throw new TimeoutException();
                  return task.call();
                }));
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      go.countDown();
      List<T> output = new ArrayList<>();
      for (Future<T> result : results) output.add(result.get(30, TimeUnit.SECONDS));
      return output;
    } finally {
      go.countDown();
      pool.shutdownNow();
    }
  }
}
