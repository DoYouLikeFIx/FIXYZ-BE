package com.fix.corebank.controller;

import com.fix.common.error.ApiResponse;
import com.fix.common.web.CommonHeaders;
import com.fix.common.validation.ContractPatterns;
import com.fix.corebank.dto.request.InternalOrderCreateRequest;
import com.fix.corebank.dto.request.InternalOrderRequeryRequest;
import com.fix.corebank.dto.request.InternalPortfolioRequest;
import com.fix.corebank.dto.request.InternalPortfolioProvisioningRequest;
import com.fix.corebank.dto.response.InternalOrderResponse;
import com.fix.corebank.dto.response.InternalPortfolioResponse;
import com.fix.corebank.dto.response.InternalPortfolioProvisioningResponse;
import com.fix.corebank.service.AccountProvisioningService;
import com.fix.corebank.service.CorebankOrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/internal/v1")
public class InternalCorebankController {

  private final CorebankOrderService corebankOrderService;
  private final AccountProvisioningService accountProvisioningService;

  public InternalCorebankController(
      CorebankOrderService corebankOrderService,
      AccountProvisioningService accountProvisioningService
  ) {
    this.corebankOrderService = corebankOrderService;
    this.accountProvisioningService = accountProvisioningService;
  }

  @GetMapping("/portfolio")
  public ApiResponse<InternalPortfolioResponse> portfolio(@Valid @ModelAttribute InternalPortfolioRequest request) {
    return ApiResponse.success(InternalPortfolioResponse.from(corebankOrderService.getPortfolio(request.toVo())));
  }

  @PostMapping(value = "/portfolio", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ApiResponse<InternalPortfolioProvisioningResponse>> provisionPortfolio(
      @RequestHeader(CommonHeaders.X_CORRELATION_ID) String correlationId,
      @RequestBody InternalPortfolioProvisioningRequest request
  ) {
    InternalPortfolioProvisioningResponse response =
        InternalPortfolioProvisioningResponse.from(accountProvisioningService.provisionDefaultAccount(request.toVo()));
    HttpStatus httpStatus = response.isIdempotent() ? HttpStatus.OK : HttpStatus.CREATED;
    return ResponseEntity.status(httpStatus)
        .header(CommonHeaders.X_CORRELATION_ID, correlationId)
        .body(ApiResponse.success(response));
  }

  @PostMapping("/orders")
  public ApiResponse<InternalOrderResponse> createOrder(@Valid @ModelAttribute InternalOrderCreateRequest request) {
    return ApiResponse.success(InternalOrderResponse.from(corebankOrderService.createOrder(request.toVo())));
  }

  @GetMapping("/orders/{clOrdId}/requery")
  public ApiResponse<InternalOrderResponse> requeryOrder(
      @Pattern(regexp = ContractPatterns.UUID_V4)
      @PathVariable String clOrdId,
      @ParameterObject @Valid @ModelAttribute InternalOrderRequeryRequest request
  ) {
    return ApiResponse.success(InternalOrderResponse.from(corebankOrderService.requeryOrder(request.toVo(clOrdId))));
  }
}
