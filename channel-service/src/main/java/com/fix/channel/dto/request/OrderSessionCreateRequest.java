package com.fix.channel.dto.request;

import com.fix.channel.vo.OrderSessionCreateCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class OrderSessionCreateRequest {

  @NotNull
  private Long memberId;

  @NotBlank
  private String clOrdId;

  @NotBlank
  private String orderRef;

  public OrderSessionCreateCommand toVo() {
    return OrderSessionCreateCommand.of(memberId, clOrdId, orderRef);
  }

  public Long getMemberId() {
    return memberId;
  }

  public void setMemberId(Long memberId) {
    this.memberId = memberId;
  }

  public String getClOrdId() {
    return clOrdId;
  }

  public void setClOrdId(String clOrdId) {
    this.clOrdId = clOrdId;
  }

  public String getOrderRef() {
    return orderRef;
  }

  public void setOrderRef(String orderRef) {
    this.orderRef = orderRef;
  }
}
