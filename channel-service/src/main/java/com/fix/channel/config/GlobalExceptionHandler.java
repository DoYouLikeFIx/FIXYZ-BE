package com.fix.channel.config;

import com.fix.common.error.ApiErrorResponse;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.error.FixException;
import com.fix.common.error.SystemException;
import com.fix.common.web.CommonHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(FixException.class)
  public ResponseEntity<ApiErrorResponse> handleFixException(FixException ex, HttpServletRequest request) {
    return build(ex.getErrorCode(), ex.getMessage(), request);
  }

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
    return build(ex.getErrorCode(), ex.getMessage(), request);
  }

  @ExceptionHandler(SystemException.class)
  public ResponseEntity<ApiErrorResponse> handleSystemException(SystemException ex, HttpServletRequest request) {
    return build(ex.getErrorCode(), ex.getMessage(), request);
  }

  @ExceptionHandler({
      BindException.class,
      MethodArgumentNotValidException.class,
      ConstraintViolationException.class,
      MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<ApiErrorResponse> handleValidationException(Exception ex, HttpServletRequest request) {
    return build(ErrorCode.VALIDATION_FAILED, resolveValidationMessage(ex), request);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
    return build(ErrorCode.NOT_FOUND, ex.getMessage(), request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorResponse> handleUnhandled(Exception ex, HttpServletRequest request) {
    return build(ErrorCode.INTERNAL_ERROR, ex.getMessage(), request);
  }

  private ResponseEntity<ApiErrorResponse> build(ErrorCode errorCode, String message, HttpServletRequest request) {
    String correlationId = resolveCorrelationId(request);
    ApiErrorResponse response = ApiErrorResponse.from(errorCode, message, request.getRequestURI(), correlationId);

    return ResponseEntity
        .status(errorCode.httpStatus())
        .header(CommonHeaders.X_CORRELATION_ID, correlationId)
        .body(response);
  }

  private String resolveCorrelationId(HttpServletRequest request) {
    String correlationId = request.getHeader(CommonHeaders.X_CORRELATION_ID);
    if (correlationId == null || correlationId.isBlank()) {
      return UUID.randomUUID().toString();
    }
    return correlationId;
  }

  private String resolveValidationMessage(Exception ex) {
    if (ex instanceof BindException bindException && bindException.hasFieldErrors()) {
      // 팀원이 로그/응답만 보고도 원인을 파악할 수 있도록 첫 번째 필드 오류 메시지를 우선 노출한다.
      String message = bindException.getFieldErrors().getFirst().getDefaultMessage();
      if (message != null && !message.isBlank()) {
        return message;
      }
    }
    if (ex instanceof MethodArgumentNotValidException methodArgumentNotValidException
        && methodArgumentNotValidException.hasFieldErrors()) {
      String message = methodArgumentNotValidException.getFieldErrors().getFirst().getDefaultMessage();
      if (message != null && !message.isBlank()) {
        return message;
      }
    }
    return ErrorCode.VALIDATION_FAILED.defaultMessage();
  }
}
