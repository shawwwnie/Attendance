package dev.careflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Own context: MockMvc's csrf() postprocessor replaces the filter's repository in other tests. This
 * test exercises the configured cookie repository and form authentication without that helper.
 */
@SpringBootTest(properties = "careflow.authentication-test=true")
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class AuthenticationIntegrationTest {
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

  @Test
  void cookieCsrfLoginRotationMutationAndLogout() throws Exception {
    MvcResult csrf = mvc.perform(get("/api/csrf")).andExpect(status().isOk()).andReturn();
    String firstToken = body(csrf).path("token").asText();
    assertThat(csrf.getResponse().getHeaders("Set-Cookie"))
        .anySatisfy(value -> assertThat(value).contains("XSRF-TOKEN=" + firstToken));
    MvcResult login =
        mvc.perform(
                post("/api/login")
                    .cookie(new Cookie("XSRF-TOKEN", firstToken))
                    .header("X-XSRF-TOKEN", firstToken)
                    .param("username", "manager")
                    .param("password", "careflow-demo"))
            .andExpect(status().isOk())
            .andReturn();
    MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
    assertThat(session).isNotNull();
    mvc.perform(get("/api/session").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.centerId").value(1));
    MvcResult refreshed =
        mvc.perform(get("/api/csrf").session(session)).andExpect(status().isOk()).andReturn();
    String nextToken = body(refreshed).path("token").asText();
    assertThat(nextToken).isNotEqualTo(firstToken);
    mvc.perform(
            post("/api/recipients")
                .session(session)
                .cookie(new Cookie("XSRF-TOKEN", nextToken))
                .header("X-XSRF-TOKEN", nextToken)
                .contentType("application/json")
                .content(
                    "{\"name\":\"인증"
                        + " 샘플\",\"careGrade\":3,\"dailyFee\":60000,\"copayBasisPoints\":1500}"))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/logout")
                .session(session)
                .cookie(new Cookie("XSRF-TOKEN", nextToken))
                .header("X-XSRF-TOKEN", nextToken))
        .andExpect(status().isNoContent());
    assertThat(session.isInvalid()).isTrue();
  }

  private JsonNode body(MvcResult result) throws Exception {
    return json.readTree(
        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
  }
}
