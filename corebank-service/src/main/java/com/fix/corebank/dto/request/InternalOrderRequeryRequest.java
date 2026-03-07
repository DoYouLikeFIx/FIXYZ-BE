package com.fix.corebank.dto.request;

import com.fix.corebank.vo.InternalOrderRequeryCommand;

public class InternalOrderRequeryRequest {

  public InternalOrderRequeryCommand toVo(String clOrdId) {
    return InternalOrderRequeryCommand.of(clOrdId);
  }
}
