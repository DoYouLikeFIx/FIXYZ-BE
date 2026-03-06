package com.fix.channel.vo;

import java.util.List;

public class AdminSecurityEventResult {

  private final List<SecurityEventItemVo> items;

  private AdminSecurityEventResult(List<SecurityEventItemVo> items) {
    this.items = items;
  }

  public static AdminSecurityEventResult of(List<SecurityEventItemVo> items) {
    return new AdminSecurityEventResult(items);
  }

  public List<SecurityEventItemVo> getItems() {
    return items;
  }
}
