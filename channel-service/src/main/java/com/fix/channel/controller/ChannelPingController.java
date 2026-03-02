package com.fix.channel.controller;

import com.fix.common.error.ErrorCode;
import com.fix.common.error.FixException;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChannelPingController {

  @GetMapping("/api/v1/ping")
  public Map<String, String> ping() {
    return Map.of("service", "channel", "status", "ok");
  }

  @GetMapping("/api/v1/errors/boom")
  public void boom() {
    throw new FixException(ErrorCode.BAD_REQUEST, "channel bad request");
  }
}
