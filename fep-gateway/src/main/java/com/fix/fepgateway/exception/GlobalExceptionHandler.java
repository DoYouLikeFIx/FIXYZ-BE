package com.fix.fepgateway.exception;

import com.fix.common.error.ApiErrorResponse;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.error.SystemException;
import com.fix.common.web.CommonHeaders;
import com.fix.common.web.CorrelationIdSupport;
import com.fix.common.web.TraceparentSupport;
import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
    return build(ex.getErrorCode(), ex.getMessage(), ex.getMetadata(), ex.getDetails(), request);
  }

  @ExceptionHandler(SystemException.class)
  public ResponseEntity<ApiErrorResponse> handleSystemException(SystemException ex, HttpServletRequest request) {
    return build(ex.getErrorCode(), ex.getMessage(), ex.getMetadata(), null, request);
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
    return build(errorCode, message, null, null, request);
  }

  private ResponseEntity<ApiErrorResponse> build(
      ErrorCode errorCode,
      String message,
      com.fix.common.error.ErrorMetadata metadata,
      Map<String, Object> details,
      HttpServletRequest request
  ) {
    String correlationId = CorrelationIdSupport.ensureCorrelationId(request);
    ApiErrorResponse response = ApiErrorResponse.from(
        errorCode,
        message,
        request.getRequestURI(),
        correlationId,
        metadata,
        details
    );

    return ResponseEntity
        .status(errorCode.httpStatus())
        .header(CommonHeaders.X_CORRELATION_ID, correlationId)
        .header(CommonHeaders.TRACEPARENT, TraceparentSupport.ensureTraceparent(request))
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

}
