package com.fix.fepgateway.dataplane.marketdata.kis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class KisWebSocketControlMessageParserTest {

  private final KisWebSocketControlMessageParser parser =
      new KisWebSocketControlMessageParser(new ObjectMapper());

  @Test
  void shouldParseSubscribeSuccessWithDecryptionContext() {
    String message = """
        {
          "header": {
            "tr_id": "H0STCNT0"
          },
          "body": {
            "msg1": "SUBSCRIBE SUCCESS",
            "output": {
              "key": "12345678901234567890123456789012",
              "iv": "1234567890123456"
            }
          }
        }
        """;

    KisWebSocketControlMessage controlMessage = parser.parse(message).orElseThrow();

    assertThat(controlMessage.trId()).isEqualTo("H0STCNT0");
    assertThat(controlMessage.isSubscribeSuccess()).isTrue();
    assertThat(controlMessage.hasDecryptionContext()).isTrue();
    assertThat(controlMessage.decryptionContext().key()).isEqualTo("12345678901234567890123456789012");
  }

  @Test
  void shouldReturnEmptyWhenBodyMessageIsMissing() {
    assertThat(parser.parse("""
        {
          "header": {
            "tr_id": "H0STCNT0"
          },
          "body": {
            "rt_cd": "0"
          }
        }
        """)).isEmpty();
  }

  @Test
  void shouldFailWhenSubscribeSuccessIsMissingTrId() {
    assertThatThrownBy(() -> parser.parse("""
        {
          "body": {
            "msg1": "SUBSCRIBE SUCCESS",
            "output": {
              "key": "12345678901234567890123456789012",
              "iv": "1234567890123456"
            }
          }
        }
        """))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tr_id");
  }
}
