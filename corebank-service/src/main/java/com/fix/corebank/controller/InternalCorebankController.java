package com.fix.corebank.controller;

import com.fix.common.error.ApiResponse;
import com.fix.common.web.CommonHeaders;
import com.fix.common.validation.ContractPatterns;
import com.fix.corebank.dto.request.InternalAccountPositionRequest;
import com.fix.corebank.dto.request.InternalAccountStatusRequest;
import com.fix.corebank.dto.request.InternalAccountStatusTransitionRequest;
import com.fix.corebank.dto.request.InternalAccountPositionsRequest;
import com.fix.corebank.dto.request.InternalAccountSummaryRequest;
import com.fix.corebank.dto.request.InternalAccountOrderHistoryRequest;
import com.fix.corebank.dto.request.InternalOrderCreateRequest;
import com.fix.corebank.dto.request.InternalOrderReplayRequest;
import com.fix.corebank.dto.request.InternalOrderRequeryRequest;
import com.fix.corebank.dto.request.InternalLedgerReconciliationCaseCreateRequest;
import com.fix.corebank.dto.request.InternalLedgerReconciliationCaseTransitionRequest;
import com.fix.corebank.dto.request.InternalLedgerReconciliationRepairRequest;
import com.fix.corebank.dto.request.InternalLedgerReconciliationRerunRequest;
import com.fix.corebank.dto.request.InternalPortfolioRequest;
import com.fix.corebank.dto.request.InternalPortfolioProvisioningRequest;
import com.fix.corebank.dto.response.InternalAccountOrderHistoryResponse;
import com.fix.corebank.dto.response.InternalAccountPositionResponse;
import com.fix.corebank.dto.response.InternalAccountStatusResponse;
import com.fix.corebank.dto.response.InternalAccountStatusTransitionResponse;
import com.fix.corebank.dto.response.InternalLedgerReconciliationCaseResponse;
import com.fix.corebank.dto.response.InternalLedgerReconciliationRepairResponse;
import com.fix.corebank.dto.response.InternalLedgerReconciliationRerunResponse;
import com.fix.corebank.dto.response.InternalOrderResponse;
import com.fix.corebank.dto.response.InternalOrderReplayResponse;
import com.fix.corebank.dto.response.InternalPortfolioResponse;
import com.fix.corebank.dto.response.InternalPortfolioProvisioningResponse;
import com.fix.corebank.service.AccountProvisioningService;
import com.fix.corebank.service.CorebankOrderService;
import com.fix.corebank.service.CorebankOrderReplayService;
import com.fix.corebank.service.LedgerReconciliationService;
import com.fix.corebank.service.LedgerRepairService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@Validated
@RequestMapping("/internal/v1")
public class InternalCorebankController {

  private final CorebankOrderService corebankOrderService;
  private final CorebankOrderReplayService corebankOrderReplayService;
  private final AccountProvisioningService accountProvisioningService;
  private final LedgerReconciliationService ledgerReconciliationService;
  private final LedgerRepairService ledgerRepairService;

  public InternalCorebankController(
      CorebankOrderService corebankOrderService,
      CorebankOrderReplayService corebankOrderReplayService,
      AccountProvisioningService accountProvisioningService,
      LedgerReconciliationService ledgerReconciliationService,
      LedgerRepairService ledgerRepairService
  ) {
    this.corebankOrderService = corebankOrderService;
    this.corebankOrderReplayService = corebankOrderReplayService;
    this.accountProvisioningService = accountProvisioningService;
    this.ledgerReconciliationService = ledgerReconciliationService;
    this.ledgerRepairService = ledgerRepairService;
  }

  @GetMapping("/portfolio")
  public ApiResponse<InternalPortfolioResponse> portfolio(@Valid @ModelAttribute InternalPortfolioRequest request) {
    return ApiResponse.success(InternalPortfolioResponse.from(corebankOrderService.getPortfolio(request.toVo())));
  }

  @GetMapping("/accounts/{accountId}/positions")
  public ApiResponse<InternalAccountPositionResponse> accountPosition(
      @PathVariable Long accountId,
      @Valid @ModelAttribute InternalAccountPositionRequest request
  ) {
    return ApiResponse.success(InternalAccountPositionResponse.from(
        corebankOrderService.getAccountPosition(request.toVo(accountId))
    ));
  }

  @GetMapping("/accounts/{accountId}/summary")
  public ApiResponse<InternalAccountPositionResponse> accountSummary(
      @PathVariable Long accountId,
      @Valid @ModelAttribute InternalAccountSummaryRequest request
  ) {
    return ApiResponse.success(InternalAccountPositionResponse.from(
        corebankOrderService.getAccountSummary(request.toVo(accountId))
    ));
  }

  @GetMapping("/accounts/{accountId}/positions/list")
  public ApiResponse<List<InternalAccountPositionResponse>> accountPositions(
      @PathVariable Long accountId,
      @Valid @ModelAttribute InternalAccountPositionsRequest request
  ) {
    return ApiResponse.success(corebankOrderService.getAccountPositions(request.toVo(accountId)).stream()
        .map(InternalAccountPositionResponse::from)
        .toList());
  }

  @GetMapping("/accounts/{accountId}/status")
  public ApiResponse<InternalAccountStatusResponse> accountStatus(
      @PathVariable Long accountId,
      @Valid @ModelAttribute InternalAccountStatusRequest request
  ) {
    return ApiResponse.success(InternalAccountStatusResponse.from(
        corebankOrderService.getAccountStatus(request.toVo(accountId))
    ));
  }

  @GetMapping("/accounts/default")
  public ApiResponse<InternalAccountStatusResponse> defaultAccountStatus(
      @Valid @ModelAttribute InternalAccountStatusRequest request
  ) {
    return ApiResponse.success(InternalAccountStatusResponse.from(
        corebankOrderService.getDefaultAccountStatus(request.getMemberId())
    ));
  }

  @PatchMapping(value = "/accounts/{accountId}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ApiResponse<InternalAccountStatusTransitionResponse> transitionAccountStatus(
      @PathVariable Long accountId,
      @RequestHeader(CommonHeaders.X_CORRELATION_ID) String correlationId,
      @Valid @RequestBody InternalAccountStatusTransitionRequest request
  ) {
    return ApiResponse.success(InternalAccountStatusTransitionResponse.from(
        corebankOrderService.transitionAccountStatus(request.toVo(accountId, correlationId))
    ));
  }

  @GetMapping("/accounts/{accountId}/orders")
  public ApiResponse<InternalAccountOrderHistoryResponse> accountOrderHistory(
      @PathVariable Long accountId,
      @Valid @ModelAttribute InternalAccountOrderHistoryRequest request
  ) {
    return ApiResponse.success(InternalAccountOrderHistoryResponse.from(
        corebankOrderService.getAccountOrderHistory(request.toVo(accountId))
    ));
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

  @PostMapping(value = "/orders/{clOrdId}/replay", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ApiResponse<InternalOrderReplayResponse> replayOrder(
      @Pattern(regexp = ContractPatterns.UUID_V4)
      @PathVariable String clOrdId,
      @RequestHeader(CommonHeaders.X_CORRELATION_ID) String correlationId,
      @Valid @RequestBody InternalOrderReplayRequest request
  ) {
    return ApiResponse.success(InternalOrderReplayResponse.from(
        corebankOrderReplayService.replay(request.toVo(clOrdId, correlationId))
    ));
  }

  @PostMapping(value = "/ledger-integrity/anomalies/{anomalyId}/cases", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ApiResponse<InternalLedgerReconciliationCaseResponse> createLedgerReconciliationCase(
      @PathVariable Long anomalyId,
      @RequestHeader(CommonHeaders.X_CORRELATION_ID) String correlationId,
      @Valid @RequestBody InternalLedgerReconciliationCaseCreateRequest request
  ) {
    return ApiResponse.success(InternalLedgerReconciliationCaseResponse.from(
        ledgerReconciliationService.createCase(request.toVo(anomalyId, correlationId))
    ));
  }

  @PatchMapping(value = "/ledger-integrity/cases/{caseId}", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ApiResponse<InternalLedgerReconciliationCaseResponse> transitionLedgerReconciliationCase(
      @PathVariable Long caseId,
      @RequestHeader(CommonHeaders.X_CORRELATION_ID) String correlationId,
      @Valid @RequestBody InternalLedgerReconciliationCaseTransitionRequest request
  ) {
    return ApiResponse.success(InternalLedgerReconciliationCaseResponse.from(
        ledgerReconciliationService.transitionCase(request.toVo(caseId, correlationId))
    ));
  }

  @PostMapping(value = "/ledger-integrity/cases/{caseId}/repairs", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ApiResponse<InternalLedgerReconciliationRepairResponse> applyLedgerRepair(
      @PathVariable Long caseId,
      @RequestHeader(CommonHeaders.X_CORRELATION_ID) String correlationId,
      @Valid @RequestBody InternalLedgerReconciliationRepairRequest request
  ) {
    return ApiResponse.success(InternalLedgerReconciliationRepairResponse.from(
        ledgerRepairService.applyRepair(request.toVo(caseId, correlationId))
    ));
  }

  @PostMapping(value = "/ledger-integrity/cases/{caseId}/rerun", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ApiResponse<InternalLedgerReconciliationRerunResponse> rerunLedgerReconciliationCase(
      @PathVariable Long caseId,
      @RequestHeader(CommonHeaders.X_CORRELATION_ID) String correlationId,
      @Valid @RequestBody InternalLedgerReconciliationRerunRequest request
  ) {
    return ApiResponse.success(InternalLedgerReconciliationRerunResponse.from(
        ledgerRepairService.rerunCase(request.toVo(caseId, correlationId))
    ));
  }
}
