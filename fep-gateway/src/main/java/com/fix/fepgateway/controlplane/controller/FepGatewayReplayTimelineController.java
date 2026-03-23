package com.fix.fepgateway.controlplane.controller;

import com.fix.common.error.ApiResponse;
import com.fix.common.web.CommonHeaders;
import com.fix.fepgateway.controlplane.service.FepGatewayReplayTimelineService;
import com.fix.fepgateway.dto.request.FepReplayTimelineStartRequest;
import com.fix.fepgateway.dto.response.FepReplayTimelineResponse;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/fep-internal/v1/market-data/replay/timelines")
public class FepGatewayReplayTimelineController {

  private final FepGatewayReplayTimelineService fepGatewayReplayTimelineService;

  public FepGatewayReplayTimelineController(FepGatewayReplayTimelineService fepGatewayReplayTimelineService) {
    this.fepGatewayReplayTimelineService = fepGatewayReplayTimelineService;
  }

  @PostMapping
  public ApiResponse<FepReplayTimelineResponse> startTimeline(
      @RequestHeader(CommonHeaders.X_INTERNAL_SECRET) String internalSecret,
      @RequestHeader(CommonHeaders.X_CORRELATION_ID) String correlationId,
      @Valid @RequestBody FepReplayTimelineStartRequest request
  ) {
    return ApiResponse.success(
        FepReplayTimelineResponse.from(fepGatewayReplayTimelineService.startTimeline(request.toVo()))
    );
  }

  @GetMapping("/{replayId}")
  public ApiResponse<FepReplayTimelineResponse> getTimeline(
      @RequestHeader(CommonHeaders.X_INTERNAL_SECRET) String internalSecret,
      @RequestHeader(CommonHeaders.X_CORRELATION_ID) String correlationId,
      @PathVariable String replayId
  ) {
    return ApiResponse.success(
        FepReplayTimelineResponse.from(fepGatewayReplayTimelineService.getTimeline(replayId))
    );
  }

  @PostMapping("/{replayId}/pause")
  public ApiResponse<FepReplayTimelineResponse> pauseTimeline(
      @RequestHeader(CommonHeaders.X_INTERNAL_SECRET) String internalSecret,
      @RequestHeader(CommonHeaders.X_CORRELATION_ID) String correlationId,
      @PathVariable String replayId
  ) {
    return ApiResponse.success(
        FepReplayTimelineResponse.from(fepGatewayReplayTimelineService.pauseTimeline(replayId))
    );
  }

  @PostMapping("/{replayId}/resume")
  public ApiResponse<FepReplayTimelineResponse> resumeTimeline(
      @RequestHeader(CommonHeaders.X_INTERNAL_SECRET) String internalSecret,
      @RequestHeader(CommonHeaders.X_CORRELATION_ID) String correlationId,
      @PathVariable String replayId
  ) {
    return ApiResponse.success(
        FepReplayTimelineResponse.from(fepGatewayReplayTimelineService.resumeTimeline(replayId))
    );
  }
}
