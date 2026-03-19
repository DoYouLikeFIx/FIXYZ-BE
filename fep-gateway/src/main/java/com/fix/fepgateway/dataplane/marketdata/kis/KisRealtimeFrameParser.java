package com.fix.fepgateway.dataplane.marketdata.kis;

import org.springframework.stereotype.Component;

@Component
public class KisRealtimeFrameParser {

  private static final int FRAME_SEGMENT_COUNT = 4;

  public KisRealtimeFrame parse(String rawFrame) {
    if (rawFrame == null || rawFrame.isBlank()) {
      throw new KisFrameParseException(
          KisFrameFailureType.MALFORMED_FRAME,
          "KIS realtime frame must not be blank"
      );
    }

    String[] segments = rawFrame.strip().split("\\|", FRAME_SEGMENT_COUNT);
    if (segments.length != FRAME_SEGMENT_COUNT) {
      throw new KisFrameParseException(
          KisFrameFailureType.MALFORMED_FRAME,
          "KIS realtime frame must contain encFlag|trId|count|payload"
      );
    }

    try {
      return new KisRealtimeFrame(
          parseEncryptedFlag(segments[0]),
          segments[1],
          parseRecordCount(segments[2]),
          segments[3]
      );
    } catch (IllegalArgumentException exception) {
      throw new KisFrameParseException(
          KisFrameFailureType.MALFORMED_FRAME,
          exception.getMessage(),
          exception
      );
    }
  }

  private boolean parseEncryptedFlag(String rawFlag) {
    return switch (rawFlag) {
      case "0" -> false;
      case "1" -> true;
      default -> throw new KisFrameParseException(
          KisFrameFailureType.MALFORMED_FRAME,
          "Unsupported KIS encFlag: " + rawFlag
      );
    };
  }

  private int parseRecordCount(String rawCount) {
    try {
      int recordCount = Integer.parseInt(rawCount);
      if (recordCount < 1) {
        throw new KisFrameParseException(
            KisFrameFailureType.MALFORMED_FRAME,
            "KIS realtime record count must be greater than zero"
        );
      }
      return recordCount;
    } catch (NumberFormatException exception) {
      throw new KisFrameParseException(
          KisFrameFailureType.MALFORMED_FRAME,
          "Invalid KIS realtime record count: " + rawCount,
          exception
      );
    }
  }
}
