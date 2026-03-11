package com.fix.channel.dto.request;

import com.fix.channel.vo.AccountOrderHistoryQueryCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class AccountOrderHistoryQueryRequest {

  @Min(0)
  private Integer page = 0;

  @Min(1)
  @Max(100)
  private Integer size = 20;

  public AccountOrderHistoryQueryCommand toVo(Long accountId, Long memberId) {
    int resolvedPage = page == null ? 0 : page;
    int resolvedSize = size == null ? 20 : size;
    return AccountOrderHistoryQueryCommand.of(accountId, memberId, resolvedPage, resolvedSize);
  }

  public Integer getPage() {
    return page;
  }

  public void setPage(Integer page) {
    this.page = page;
  }

  public Integer getSize() {
    return size;
  }

  public void setSize(Integer size) {
    this.size = size;
  }
}
