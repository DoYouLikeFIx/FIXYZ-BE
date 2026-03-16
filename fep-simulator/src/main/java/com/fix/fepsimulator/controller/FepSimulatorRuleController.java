package com.fix.fepsimulator.controller;

import com.fix.common.error.ApiResponse;
import com.fix.fepsimulator.dto.request.SimulatorRuleUpsertRequest;
import com.fix.fepsimulator.dto.response.SimulatorRuleClearResponse;
import com.fix.fepsimulator.dto.response.SimulatorRuleListResponse;
import com.fix.fepsimulator.dto.response.SimulatorRuleResponse;
import com.fix.fepsimulator.service.FepSimulatorControlService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!prod")
@RequestMapping("/fep-internal")
public class FepSimulatorRuleController {

  private final FepSimulatorControlService fepSimulatorControlService;

  public FepSimulatorRuleController(FepSimulatorControlService fepSimulatorControlService) {
    this.fepSimulatorControlService = fepSimulatorControlService;
  }

  @PutMapping("/rules")
  public ApiResponse<SimulatorRuleResponse> upsertRule(
      @RequestBody SimulatorRuleUpsertRequest request,
      HttpServletRequest httpServletRequest
  ) {
    return ApiResponse.success(SimulatorRuleResponse.from(
        fepSimulatorControlService.applyRule(request.toVo(), httpServletRequest.getRequestURI(), httpServletRequest.getRemoteAddr())
    ));
  }

  @GetMapping("/rules")
  public ApiResponse<SimulatorRuleListResponse> getRuleList() {
    List<SimulatorRuleResponse> activeRules = fepSimulatorControlService.listActiveRules().stream()
        .map(SimulatorRuleResponse::from)
        .toList();
    return ApiResponse.success(new SimulatorRuleListResponse(activeRules));
  }

  @DeleteMapping("/rules")
  public ApiResponse<SimulatorRuleClearResponse> clearRules(HttpServletRequest httpServletRequest) {
    int clearedCount = fepSimulatorControlService.clearRules(httpServletRequest.getRequestURI(), httpServletRequest.getRemoteAddr());
    return ApiResponse.success(new SimulatorRuleClearResponse("All active chaos rules were cleared.", clearedCount));
  }
}
