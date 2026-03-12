package com.fix.channel.exception;

import com.fix.common.error.ApiErrorResponse;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.error.ErrorMetadata;
import com.fix.common.error.FixException;
import com.fix.common.error.RetryAfterBusinessException;
import com.fix.common.error.SystemException;
import com.fix.common.web.CommonHeaders;
import com.fix.common.web.CorrelationIdSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(FixException.class)
  public ResponseEntity<ApiErrorResponse> handleFixException(FixException ex, HttpServletRequest request) {
    return build(ex.getErrorCode(), ex.getMessage(), ex.getMetadata(), request, null);
  }

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
    Long retryAfterSeconds = null;
    if (ex instanceof RetryAfterBusinessException retryAfterBusinessException) {
      retryAfterSeconds = retryAfterBusinessException.getRetryAfterSeconds();
    }
    return build(ex.getErrorCode(), ex.getMessage(), ex.getMetadata(), request, retryAfterSeconds);
  }

  @ExceptionHandler(SystemException.class)
  public ResponseEntity<ApiErrorResponse> handleSystemException(SystemException ex, HttpServletRequest request) {
    return build(ex.getErrorCode(), ex.getMessage(), ex.getMetadata(), request, null);
  }

  @ExceptionHandler({
      BindException.class,
      MethodArgumentNotValidException.class,
      ConstraintViolationException.class,
      ServletRequestBindingException.class,
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
    return build(errorCode, message, null, request, null);
  }

  private ResponseEntity<ApiErrorResponse> build(
      ErrorCode errorCode,
      String message,
      ErrorMetadata metadata,
      HttpServletRequest request,
      Long retryAfterSeconds
  ) {
    String correlationId = CorrelationIdSupport.ensureCorrelationId(request);
    ApiErrorResponse response = ApiErrorResponse.from(
        errorCode,
        message,
        request.getRequestURI(),
        correlationId,
        metadata
    );

    ResponseEntity.BodyBuilder builder = ResponseEntity
        .status(errorCode.httpStatus())
        .header(CommonHeaders.X_CORRELATION_ID, correlationId);
    if (retryAfterSeconds != null && retryAfterSeconds > 0) {
      builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
    }

    return builder.body(response);
  }

  private String resolveValidationMessage(Exception ex) {
    if (ex instanceof BindException bindException && bindException.hasFieldErrors()) {
      String message = bindException.getFieldErrors().getFirst().getDefaultMessage();
      if (message != null && !message.isBlank()) {
        return message;
      }
    }
    if (ex instanceof BindException bindException && bindException.hasGlobalErrors()) {
      String message = bindException.getGlobalErrors().getFirst().getDefaultMessage();
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
    if (ex instanceof MethodArgumentNotValidException methodArgumentNotValidException
        && methodArgumentNotValidException.hasGlobalErrors()) {
      String message = methodArgumentNotValidException.getGlobalErrors().getFirst().getDefaultMessage();
      if (message != null && !message.isBlank()) {
        return message;
      }
    }
    if (ex instanceof ConstraintViolationException constraintViolationException
        && !constraintViolationException.getConstraintViolations().isEmpty()) {
      String message = constraintViolationException.getConstraintViolations().iterator().next().getMessage();
      if (message != null && !message.isBlank()) {
        return message;
      }
    }
    return ErrorCode.VALIDATION_FAILED.defaultMessage();
  }
}
