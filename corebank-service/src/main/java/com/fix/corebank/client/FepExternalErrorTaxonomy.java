package com.fix.corebank.client;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.error.ErrorMetadata;
import java.util.Map;

public final class FepExternalErrorTaxonomy {

  private static final Map<String, TaxonomyEntry> MAPPINGS = Map.of(
      "9001", TaxonomyEntry.of(ErrorCode.CHANNEL_ROUTE_NOT_FOUND, "error.channel.route_not_found", "NO_ROUTE"),
      "9002", TaxonomyEntry.of(ErrorCode.FEP_GATEWAY_UNAVAILABLE, "error.fep.unavailable", "POOL_EXHAUSTED"),
      "9003", TaxonomyEntry.of(ErrorCode.FEP_GATEWAY_UNAVAILABLE, "error.fep.unavailable", "NOT_LOGGED_ON"),
      "9004", TaxonomyEntry.of(ErrorCode.FEP_GATEWAY_TIMEOUT, "error.fep.timeout", "TIMEOUT"),
      "9005", TaxonomyEntry.of(ErrorCode.FEP_GATEWAY_UNAVAILABLE, "error.fep.unavailable", "KEY_EXPIRED"),
      "9097", TaxonomyEntry.of(ErrorCode.FEP_ORDER_REJECTED, "error.fep.rejected", "ORDER_REJECTED"),
      "9098", TaxonomyEntry.of(ErrorCode.FEP_GATEWAY_UNAVAILABLE, "error.fep.unavailable", "CIRCUIT_OPEN"),
      "9099", TaxonomyEntry.of(
          ErrorCode.CORE_CONCURRENCY_CONFLICT,
          "error.core.concurrency_conflict",
          "CONCURRENCY_FAILURE"
      )
  );

  private FepExternalErrorTaxonomy() {
  }

  public static TaxonomyEntry resolve(String externalCode) {
    if (externalCode == null || externalCode.isBlank()) {
      return fallback("UNSPECIFIED");
    }
    return MAPPINGS.getOrDefault(externalCode, fallback(externalCode));
  }

  public static boolean isExternalRc(String code) {
    return code != null && code.matches("\\d{4}");
  }

  public static BusinessException toException(String externalCode, Throwable cause) {
    TaxonomyEntry entry = resolve(externalCode);
    return new BusinessException(
        entry.errorCode(),
        entry.errorCode().defaultMessage(),
        cause,
        entry.metadata()
    );
  }

  private static TaxonomyEntry fallback(String externalCode) {
    return TaxonomyEntry.of(
        ErrorCode.FEP_UNKNOWN_EXTERNAL,
        "error.fep.unknown_external",
        "UNKNOWN_EXTERNAL_" + externalCode
    );
  }

  public record TaxonomyEntry(
      ErrorCode errorCode,
      ErrorMetadata metadata
  ) {
    static TaxonomyEntry of(ErrorCode errorCode, String userMessageKey, String operatorCode) {
      return new TaxonomyEntry(errorCode, new ErrorMetadata(userMessageKey, operatorCode));
    }
  }
}
