package com.fix.fepgateway.dataplane.marketdata.kis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class KisWebSocketControlMessageParser {

  private final ObjectMapper objectMapper;

  public KisWebSocketControlMessageParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public Optional<KisWebSocketControlMessage> parse(String rawMessage) {
    if (rawMessage == null || rawMessage.isBlank()) {
      throw new IllegalArgumentException("KIS control message must not be blank");
    }

    JsonNode root = readTree(rawMessage);
    String message = textAt(root, "body", "msg1");
    if (message == null || message.isBlank()) {
      return Optional.empty();
    }

    String trId = firstNonBlank(
        textAt(root, "header", "tr_id"),
        textAt(root, "body", "tr_id"),
        textAt(root, "body", "input", "tr_id")
    );
    if (trId == null || trId.isBlank()) {
      throw new IllegalArgumentException("KIS control message missing tr_id");
    }

    String key = textAt(root, "body", "output", "key");
    String iv = textAt(root, "body", "output", "iv");
    if ((key == null) != (iv == null)) {
      throw new IllegalArgumentException("KIS control message must include both key and iv");
    }

    KisDecryptionContext decryptionContext = null;
    if (key != null && iv != null) {
      decryptionContext = new KisDecryptionContext(key, iv);
    }

    return Optional.of(new KisWebSocketControlMessage(trId, message, decryptionContext));
  }

  private JsonNode readTree(String rawMessage) {
    try {
      return objectMapper.readTree(rawMessage);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Failed to parse KIS control message JSON", exception);
    }
  }

  private static String textAt(JsonNode node, String... path) {
    JsonNode current = node;
    for (String segment : path) {
      current = current.path(segment);
    }
    if (current.isMissingNode() || current.isNull()) {
      return null;
    }
    String text = current.asText();
    return text == null || text.isBlank() ? null : text;
  }

  private static String firstNonBlank(String... candidates) {
    for (String candidate : candidates) {
      if (candidate != null && !candidate.isBlank()) {
        return candidate;
      }
    }
    return null;
  }
}
