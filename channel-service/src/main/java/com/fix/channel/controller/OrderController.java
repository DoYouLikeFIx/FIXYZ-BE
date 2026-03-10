package com.fix.channel.controller;

import com.fix.channel.dto.request.OrderCreateRequest;
import com.fix.channel.dto.response.OrderResponse;
import com.fix.channel.service.OrderExecutionService;
import com.fix.common.error.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

  private final OrderExecutionService orderExecutionService;

  public OrderController(OrderExecutionService orderExecutionService) {
    this.orderExecutionService = orderExecutionService;
  }

  @PostMapping
  public ApiResponse<OrderResponse> create(@Valid @ModelAttribute OrderCreateRequest request) {
    return ApiResponse.success(OrderResponse.from(orderExecutionService.execute(request.toVo())));
  }
}
