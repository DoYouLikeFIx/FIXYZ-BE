package com.fix.channel.dto.response;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;

public class OrderSessionResponseSerializer extends StdSerializer<OrderSessionResponse> {

  public OrderSessionResponseSerializer() {
    super(OrderSessionResponse.class);
  }

  @Override
  public void serialize(
      OrderSessionResponse value,
      JsonGenerator generator,
      SerializerProvider provider
  ) throws IOException {
    generator.writeStartObject();
    writeNullableField(generator, "orderSessionId", value.orderSessionId());
    writeNullableField(generator, "clOrdId", value.clOrdId());
    writeNullableField(generator, "status", value.status());
    generator.writeBooleanField("challengeRequired", value.challengeRequired());
    writeNullableField(generator, "authorizationReason", value.authorizationReason());
    writeNullableField(generator, "accountId", value.accountId());
    writeNullableField(generator, "symbol", value.symbol());
    writeNullableField(generator, "side", value.side());
    writeNullableField(generator, "orderType", value.orderType());
    writeNullableField(generator, "qty", value.qty());
    writeNullableField(generator, "price", value.price());
    writeIfNonNull(generator, "quoteSnapshotId", value.quoteSnapshotId());
    writeIfNonNull(generator, "quoteAsOf", value.quoteAsOf());
    writeIfNonNull(generator, "quoteSourceMode", value.quoteSourceMode());
    writeIfNonNull(generator, "preTradePrice", value.preTradePrice());

    if (isActiveStatus(value.status())) {
      writeNullableField(generator, "expiresAt", value.expiresAt());
      writeNullableField(generator, "remainingSeconds", value.remainingSeconds());
    }

    writeNullableField(generator, "executionResult", value.executionResult());
    writeNullableField(generator, "executedQty", value.executedQty());
    writeNullableField(generator, "leavesQty", value.leavesQty());
    writeNullableField(generator, "executedPrice", value.executedPrice());
    writeNullableField(generator, "externalOrderId", value.externalOrderId());
    writeNullableField(generator, "failureReason", value.failureReason());
    writeNullableField(generator, "executedAt", value.executedAt());
    writeNullableField(generator, "canceledAt", value.canceledAt());
    writeNullableField(generator, "createdAt", value.createdAt());
    writeNullableField(generator, "updatedAt", value.updatedAt());
    generator.writeEndObject();
  }

  private boolean isActiveStatus(String status) {
    return "PENDING_NEW".equals(status) || "AUTHED".equals(status);
  }

  private void writeIfNonNull(JsonGenerator generator, String fieldName, Object value) throws IOException {
    if (value != null) {
      generator.writeObjectField(fieldName, value);
    }
  }

  private void writeNullableField(JsonGenerator generator, String fieldName, Object value) throws IOException {
    generator.writeFieldName(fieldName);
    generator.writeObject(value);
  }
}
