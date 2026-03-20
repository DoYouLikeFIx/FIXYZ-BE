package com.fix.fepgateway.dataplane.marketdata.kis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class KisH0stcnt0RecordParser {

  private final KisRealtimeFrameParser realtimeFrameParser;
  private final KisPayloadDecryptor payloadDecryptor;

  @Autowired
  public KisH0stcnt0RecordParser(
      KisRealtimeFrameParser realtimeFrameParser,
      KisPayloadDecryptor payloadDecryptor
  ) {
    this.realtimeFrameParser = realtimeFrameParser;
    this.payloadDecryptor = payloadDecryptor;
  }

  public List<KisH0stcnt0Record> parse(String rawFrame) {
    return parse(rawFrame, null);
  }

  public List<KisH0stcnt0Record> parse(String rawFrame, KisDecryptionContext decryptionContext) {
    KisRealtimeFrame frame = realtimeFrameParser.parse(rawFrame);

    if (!KisH0stcnt0Record.TR_ID.equals(frame.trId())) {
      throw new KisFrameParseException(
          KisFrameFailureType.UNSUPPORTED_TR_ID,
          "Unsupported KIS realtime tr_id: " + frame.trId()
      );
    }

    String payload = frame.payload();
    if (frame.encrypted()) {
      if (decryptionContext == null) {
        throw new KisFrameParseException(
            KisFrameFailureType.DECODE_ERROR,
            "Encrypted KIS realtime frame requires decryption context"
        );
      }
      payload = payloadDecryptor.decrypt(payload, decryptionContext);
    }

    return splitRecords(payload, frame.recordCount());
  }

  private List<KisH0stcnt0Record> splitRecords(String payload, int recordCount) {
    String[] fields = payload.split("\\^", -1);
    int expectedFieldCount = recordCount * KisH0stcnt0Record.RECORD_FIELD_COUNT;

    if (fields.length != expectedFieldCount) {
      throw new KisFrameParseException(
          KisFrameFailureType.MALFORMED_PAYLOAD,
          "KIS H0STCNT0 payload field count mismatch. expected="
              + expectedFieldCount
              + ", actual="
              + fields.length
      );
    }

    List<KisH0stcnt0Record> records = new ArrayList<>(recordCount);
    for (int recordIndex = 0; recordIndex < recordCount; recordIndex++) {
      int fromIndex = recordIndex * KisH0stcnt0Record.RECORD_FIELD_COUNT;
      int toIndex = fromIndex + KisH0stcnt0Record.RECORD_FIELD_COUNT;
      records.add(new KisH0stcnt0Record(Arrays.asList(Arrays.copyOfRange(fields, fromIndex, toIndex))));
    }
    return List.copyOf(records);
  }
}
