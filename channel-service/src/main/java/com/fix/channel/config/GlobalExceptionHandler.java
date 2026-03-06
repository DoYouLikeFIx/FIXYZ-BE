package com.fix.channel.config;

import com.fix.common.error.ApiErrorResponse;
import com.fix.common.error.ErrorCode;
import com.fix.common.error.FixException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(FixException.class)
  public ResponseEntity<ApiErrorResponse> handleFixException(FixException ex, HttpServletRequest request) {
    return ResponseEntity
        .status(ex.getErrorCode().httpStatus())
        .body(ApiErrorResponse.from(ex.getErrorCode(), ex.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
    return ResponseEntity
        .status(ErrorCode.NOT_FOUND.httpStatus())
        .body(ApiErrorResponse.from(ErrorCode.NOT_FOUND, ex.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorResponse> handleUnhandled(Exception ex, HttpServletRequest request) {
    return ResponseEntity
        .status(ErrorCode.INTERNAL_ERROR.httpStatus())
        .body(ApiErrorResponse.from(ErrorCode.INTERNAL_ERROR, ex.getMessage(), request.getRequestURI()));
  }
}
