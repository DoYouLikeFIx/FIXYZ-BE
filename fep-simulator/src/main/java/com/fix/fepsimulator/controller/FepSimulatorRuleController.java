package com.fix.fepsimulator.controller;

import com.fix.common.error.ApiResponse;
import com.fix.fepsimulator.dto.request.SimulatorChaosRequest;
import com.fix.fepsimulator.dto.request.SimulatorRuleQueryRequest;
import com.fix.fepsimulator.dto.request.SimulatorRuleUpsertRequest;
import com.fix.fepsimulator.dto.response.SimulatorChaosResponse;
import com.fix.fepsimulator.dto.response.SimulatorRuleResponse;
import com.fix.fepsimulator.service.FepSimulatorControlService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/simulator/v1")
public class FepSimulatorRuleController {

  private final FepSimulatorControlService fepSimulatorControlService;

  public FepSimulatorRuleController(FepSimulatorControlService fepSimulatorControlService) {
    this.fepSimulatorControlService = fepSimulatorControlService;
  }

  @PostMapping("/rules")
  public ApiResponse<SimulatorRuleResponse> upsertRule(@Valid @ModelAttribute SimulatorRuleUpsertRequest request) {
    return ApiResponse.success(SimulatorRuleResponse.from(fepSimulatorControlService.upsertRule(request.toVo())));
  }

  @GetMapping("/rules/{ruleCode}")
  public ApiResponse<SimulatorRuleResponse> getRule(
      @PathVariable String ruleCode,
      @ModelAttribute SimulatorRuleQueryRequest request
  ) {
    return ApiResponse.success(SimulatorRuleResponse.from(fepSimulatorControlService.getRule(request.toVo(ruleCode))));
  }

  @PostMapping("/chaos")
  public ApiResponse<SimulatorChaosResponse> runChaos(@Valid @ModelAttribute SimulatorChaosRequest request) {
    return ApiResponse.success(SimulatorChaosResponse.from(fepSimulatorControlService.runChaos(request.toVo())));
  }
}
