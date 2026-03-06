package com.fix.corebank.controller;

import com.fix.common.error.ApiResponse;
import com.fix.corebank.dto.request.InternalOrderCreateRequest;
import com.fix.corebank.dto.request.InternalOrderRequeryRequest;
import com.fix.corebank.dto.request.InternalPortfolioRequest;
import com.fix.corebank.dto.response.InternalOrderResponse;
import com.fix.corebank.dto.response.InternalPortfolioResponse;
import com.fix.corebank.service.CorebankOrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1")
public class InternalCorebankController {

  private final CorebankOrderService corebankOrderService;

  public InternalCorebankController(CorebankOrderService corebankOrderService) {
    this.corebankOrderService = corebankOrderService;
  }

  @GetMapping("/portfolio")
  public ApiResponse<InternalPortfolioResponse> portfolio(@Valid @ModelAttribute InternalPortfolioRequest request) {
    return ApiResponse.success(InternalPortfolioResponse.from(corebankOrderService.getPortfolio(request.toVo())));
  }

  @PostMapping("/orders")
  public ApiResponse<InternalOrderResponse> createOrder(@Valid @ModelAttribute InternalOrderCreateRequest request) {
    return ApiResponse.success(InternalOrderResponse.from(corebankOrderService.createOrder(request.toVo())));
  }

  @GetMapping("/orders/{clOrdId}/requery")
  public ApiResponse<InternalOrderResponse> requeryOrder(
      @PathVariable String clOrdId,
      @ModelAttribute InternalOrderRequeryRequest request
  ) {
    return ApiResponse.success(InternalOrderResponse.from(corebankOrderService.requeryOrder(request.toVo(clOrdId))));
  }
}
