package dev.careflow.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  public record ApiError(
      String code, String message, String requestId, Map<String, String> fieldErrors) {}

  @ExceptionHandler(BusinessException.class)
  ResponseEntity<ApiError> business(BusinessException e, HttpServletRequest req) {
    return error(e.getStatus(), e.getCode(), e.getMessage(), req, Map.of());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> validation(MethodArgumentNotValidException e, HttpServletRequest req) {
    Map<String, String> fields = new LinkedHashMap<>();
    e.getBindingResult()
        .getFieldErrors()
        .forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "입력값을 확인해주세요.", req, fields);
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    ServletRequestBindingException.class,
    MethodArgumentTypeMismatchException.class,
    IllegalArgumentException.class
  })
  ResponseEntity<ApiError> malformed(Exception e, HttpServletRequest req) {
    return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 형식 또는 날짜를 확인해주세요.", req, Map.of());
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ApiError> missingRoute(NoResourceFoundException e, HttpServletRequest req) {
    return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "요청한 경로를 찾을 수 없습니다.", req, Map.of());
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ApiError> duplicate(DataIntegrityViolationException e, HttpServletRequest req) {
    return error(
        HttpStatus.CONFLICT,
        "DATA_CONFLICT",
        "이미 처리된 데이터이거나 데이터 제약조건과 충돌합니다. 새로고침 후 확인해주세요.",
        req,
        Map.of());
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ApiError> forbidden(AccessDeniedException e, HttpServletRequest req) {
    return error(HttpStatus.FORBIDDEN, "FORBIDDEN", "조회 전용 계정은 데이터를 변경할 수 없습니다.", req, Map.of());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> unexpected(Exception e, HttpServletRequest req) {
    log.error("Unhandled API error", e);
    return error(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_ERROR",
        "처리 중 오류가 발생했습니다. 요청 ID로 문의해주세요.",
        req,
        Map.of());
  }

  private ResponseEntity<ApiError> error(
      HttpStatus status,
      String code,
      String message,
      HttpServletRequest req,
      Map<String, String> fields) {
    return ResponseEntity.status(status)
        .body(new ApiError(code, message, String.valueOf(req.getAttribute("requestId")), fields));
  }
}
