package dev.careflow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.careflow.common.ApiExceptionHandler.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  UserDetailsService users(
      @Value("${careflow.demo-enabled:false}") boolean demo,
      @Value("${careflow.demo-password:careflow-demo}") String password) {
    InMemoryUserDetailsManager users = new InMemoryUserDetailsManager();
    if (demo) {
      String encoded = new BCryptPasswordEncoder().encode(password);
      users.createUser(
          User.withUsername("manager").password("{bcrypt}" + encoded).roles("MANAGER").build());
      users.createUser(
          User.withUsername("viewer").password("{bcrypt}" + encoded).roles("VIEWER").build());
      users.createUser(
          User.withUsername("other-manager")
              .password("{bcrypt}" + encoded)
              .roles("MANAGER")
              .build());
    }
    return users;
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http, ObjectMapper json) throws Exception {
    CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrf.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));
    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/csrf", "/api/login", "/actuator/health")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .csrf(
            config ->
                config
                    .csrfTokenRepository(csrf)
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
        .formLogin(
            form ->
                form.loginProcessingUrl("/api/login")
                    .successHandler(
                        (req, res, auth) -> {
                          res.setContentType("application/json");
                          res.getWriter().write("{\"authenticated\":true}");
                        })
                    .failureHandler(
                        (req, res, e) ->
                            writeError(
                                json, req, res, 401, "LOGIN_FAILED", "아이디 또는 비밀번호를 확인해주세요.")))
        .logout(
            logout ->
                logout
                    .logoutUrl("/api/logout")
                    .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                    .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(
                        (req, res, e) ->
                            writeError(json, req, res, 401, "UNAUTHORIZED", "로그인이 필요합니다."))
                    .accessDeniedHandler(
                        (req, res, e) ->
                            writeError(json, req, res, 403, "FORBIDDEN", "권한 또는 보안 토큰을 확인해주세요.")));
    return http.build();
  }

  private void writeError(
      ObjectMapper json,
      HttpServletRequest req,
      HttpServletResponse res,
      int status,
      String code,
      String message)
      throws IOException {
    res.setStatus(status);
    res.setContentType("application/json;charset=UTF-8");
    json.writeValue(
        res.getWriter(),
        new ApiError(code, message, String.valueOf(req.getAttribute("requestId")), Map.of()));
  }
}
