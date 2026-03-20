package com.fix.fepgateway.controlplane.controller;

import com.fix.common.error.ApiResponse;
import com.fix.common.web.CommonHeaders;
import com.fix.fepgateway.controlplane.service.FepGatewayQuoteSnapshotQueryService;
import com.fix.fepgateway.dto.request.FepQuoteSnapshotBatchRequest;
import com.fix.fepgateway.dto.request.FepQuoteSnapshotLatestRequest;
import com.fix.fepgateway.dto.response.FepQuoteSnapshotResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/fep-internal/v1/quotes/snapshots")
public class FepGatewayQuoteSnapshotController {

  private final FepGatewayQuoteSnapshotQueryService fepGatewayQuoteSnapshotQueryService;

  public FepGatewayQuoteSnapshotController(FepGatewayQuoteSnapshotQueryService fepGatewayQuoteSnapshotQueryService) {
    this.fepGatewayQuoteSnapshotQueryService = fepGatewayQuoteSnapshotQueryService;
  }

  @GetMapping("/latest")
  public ApiResponse<FepQuoteSnapshotResponse> latestSnapshot(
      @RequestHeader(CommonHeaders.X_INTERNAL_SECRET) String internalSecret,
      @RequestHeader(CommonHeaders.X_CORRELATION_ID) String correlationId,
      @Valid @ModelAttribute FepQuoteSnapshotLatestRequest request
  ) {
    return ApiResponse.success(
        FepQuoteSnapshotResponse.from(fepGatewayQuoteSnapshotQueryService.getLatestSnapshot(request.toVo()))
    );
  }

  @GetMapping("/latest/batch")
  public ApiResponse<List<FepQuoteSnapshotResponse>> latestSnapshots(
      @RequestHeader(CommonHeaders.X_INTERNAL_SECRET) String internalSecret,
      @RequestHeader(CommonHeaders.X_CORRELATION_ID) String correlationId,
      @Valid @ModelAttribute FepQuoteSnapshotBatchRequest request
  ) {
    return ApiResponse.success(
        fepGatewayQuoteSnapshotQueryService.getLatestSnapshots(request.toVo()).stream()
            .map(FepQuoteSnapshotResponse::from)
            .toList()
    );
  }
}
