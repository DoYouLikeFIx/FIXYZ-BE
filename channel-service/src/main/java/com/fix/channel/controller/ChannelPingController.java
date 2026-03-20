package com.fix.channel.controller;

import com.fix.common.error.ApiErrorResponse;
import com.fix.common.error.ErrorCode;
import com.fix.common.error.FixException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
  @Operation(summary = "Boom")
  @ApiResponses({
      @ApiResponse(
          responseCode = "400",
          description = "Bad Request",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
      )
  })
  public void boom() {
    throw new FixException(ErrorCode.BAD_REQUEST, "channel bad request");
  }
}
