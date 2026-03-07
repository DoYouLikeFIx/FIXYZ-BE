package com.fix.channel.controller;

import com.fix.channel.dto.request.OrderSessionCreateRequest;
import com.fix.channel.dto.request.OrderSessionQueryRequest;
import com.fix.channel.dto.response.OrderSessionResponse;
import com.fix.channel.service.ChannelScaffoldService;
import com.fix.common.error.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders/sessions")
public class OrderSessionController {

  private final ChannelScaffoldService channelScaffoldService;

  public OrderSessionController(ChannelScaffoldService channelScaffoldService) {
    this.channelScaffoldService = channelScaffoldService;
  }

  @PostMapping
  public ApiResponse<OrderSessionResponse> create(@Valid @ModelAttribute OrderSessionCreateRequest request) {
    return ApiResponse.success(OrderSessionResponse.from(channelScaffoldService.createOrderSession(request.toVo())));
  }

  @GetMapping
  public ApiResponse<OrderSessionResponse> get(@ModelAttribute OrderSessionQueryRequest request) {
    return ApiResponse.success(OrderSessionResponse.from(channelScaffoldService.getOrderSession(request.toVo())));
  }
}
