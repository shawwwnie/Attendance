package dev.careflow.security;

import dev.careflow.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Demo identity mapping. A real installation must resolve persisted memberships. */
@Component
public class CenterContext {
  public long centerId() {
    return switch (actor()) {
      case "manager", "viewer" -> 1L;
      case "other-manager" -> 2L;
      default ->
          throw new BusinessException(HttpStatus.FORBIDDEN, "NO_CENTER", "소속 센터를 확인할 수 없습니다.");
    };
  }

  public String actor() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다.");
    }
    return auth.getName();
  }
}
