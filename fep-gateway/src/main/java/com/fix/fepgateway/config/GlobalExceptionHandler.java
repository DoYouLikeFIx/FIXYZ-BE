package com.fix.fepgateway.config;

import com.fix.common.error.ApiErrorResponse;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.error.SystemException;
import com.fix.common.web.CommonHeaders;
import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
    return build(ex.getErrorCode(), ex.getMessage(), request);
  }

  @ExceptionHandler(SystemException.class)
  public ResponseEntity<ApiErrorResponse> handleSystemException(SystemException ex, HttpServletRequest request) {
    return build(ex.getErrorCode(), ex.getMessage(), request);
  }

  @ExceptionHandler({
      MethodArgumentNotValidException.class,
      HandlerMethodValidationException.class,
      ConstraintViolationException.class,
      BindException.class,
      ServletRequestBindingException.class,
      HttpMessageNotReadableException.class
  })
  public ResponseEntity<ApiErrorResponse> handleValidationException(Exception ex, HttpServletRequest request) {
    return build(ErrorCode.CONTRACT_VALIDATION_FAILED, resolveValidationMessage(ex), request);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
    return build(ErrorCode.SYS_RESOURCE_NOT_FOUND, ex.getMessage(), request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorResponse> handleUnhandled(Exception ex, HttpServletRequest request) {
    return build(ErrorCode.SYS_INTERNAL_ERROR, null, request);
  }

  private ResponseEntity<ApiErrorResponse> build(ErrorCode errorCode, String message, HttpServletRequest request) {
    String correlationId = resolveCorrelationId(request);
    ApiErrorResponse response = ApiErrorResponse.from(errorCode, message, request.getRequestURI(), correlationId);

    return ResponseEntity
        .status(errorCode.httpStatus())
        .header(CommonHeaders.X_CORRELATION_ID, correlationId)
        .body(response);
  }

  private String resolveValidationMessage(Exception ex) {
    if (ex instanceof MethodArgumentNotValidException methodArgumentNotValidException
        && methodArgumentNotValidException.getBindingResult().getFieldError() != null) {
      return methodArgumentNotValidException.getBindingResult().getFieldError().getField() + " is invalid";
    }
    if (ex instanceof BindException bindException && bindException.getBindingResult().getFieldError() != null) {
      return bindException.getBindingResult().getFieldError().getField() + " is invalid";
    }
    return ErrorCode.CONTRACT_VALIDATION_FAILED.defaultMessage();
  }

  private String resolveCorrelationId(HttpServletRequest request) {
    String correlationId = request.getHeader(CommonHeaders.X_CORRELATION_ID);
    if (correlationId == null || correlationId.isBlank()) {
      return UUID.randomUUID().toString();
    }
    return correlationId;
  }
}
