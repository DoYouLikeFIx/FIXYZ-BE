package com.fix.corebank.config;

import com.fix.common.error.ApiErrorResponse;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.error.FixException;
import com.fix.common.error.SystemException;
import com.fix.common.web.CommonHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.util.UUID;

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
}
