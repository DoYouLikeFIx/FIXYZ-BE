package com.fix.channel.config;

import com.fix.common.error.ApiErrorResponse;
import com.fix.common.error.ErrorCode;
import com.fix.common.error.FixException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(FixException.class)
  public ResponseEntity<ApiErrorResponse> handleFixException(FixException ex, HttpServletRequest request) {
    HttpStatus status = switch (ex.getErrorCode()) {
      case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
      case NOT_FOUND -> HttpStatus.NOT_FOUND;
      case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
      case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
    };

    return ResponseEntity
        .status(status)
        .body(ApiErrorResponse.from(ex.getErrorCode(), ex.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
    return ResponseEntity
        .status(HttpStatus.NOT_FOUND)
        .body(ApiErrorResponse.from(ErrorCode.NOT_FOUND, ex.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorResponse> handleUnhandled(Exception ex, HttpServletRequest request) {
    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiErrorResponse.from(ErrorCode.INTERNAL_ERROR, ex.getMessage(), request.getRequestURI()));
  }
}
