package com.fix.corebank.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fix.common.error.ErrorCode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FepExternalErrorTaxonomyTest {

  private static final Map<String, FepExternalErrorTaxonomy.TaxonomyEntry> EXPECTED = Map.of(
      "9002", FepExternalErrorTaxonomy.TaxonomyEntry.of(
          ErrorCode.FEP_GATEWAY_UNAVAILABLE,
          "error.fep.unavailable",
          "POOL_EXHAUSTED"
      ),
      "9003", FepExternalErrorTaxonomy.TaxonomyEntry.of(
          ErrorCode.FEP_GATEWAY_UNAVAILABLE,
          "error.fep.unavailable",
          "NOT_LOGGED_ON"
      ),
      "9004", FepExternalErrorTaxonomy.TaxonomyEntry.of(
          ErrorCode.FEP_GATEWAY_TIMEOUT,
          "error.fep.timeout",
          "TIMEOUT"
      ),
      "9005", FepExternalErrorTaxonomy.TaxonomyEntry.of(
          ErrorCode.FEP_GATEWAY_UNAVAILABLE,
          "error.fep.unavailable",
          "KEY_EXPIRED"
      ),
      "9097", FepExternalErrorTaxonomy.TaxonomyEntry.of(
          ErrorCode.FEP_ORDER_REJECTED,
          "error.fep.rejected",
          "ORDER_REJECTED"
      ),
      "9098", FepExternalErrorTaxonomy.TaxonomyEntry.of(
          ErrorCode.FEP_GATEWAY_UNAVAILABLE,
          "error.fep.unavailable",
          "CIRCUIT_OPEN"
      )
  );

  @Test
  void shouldResolveMappedGatewayErrorsDeterministically() {
    EXPECTED.forEach((externalCode, entry) -> {
      FepExternalErrorTaxonomy.TaxonomyEntry actual = FepExternalErrorTaxonomy.resolve(externalCode);

      assertThat(actual.errorCode()).isEqualTo(entry.errorCode());
      assertThat(actual.metadata().userMessageKey()).isEqualTo(entry.metadata().userMessageKey());
      assertThat(actual.metadata().operatorCode()).isEqualTo(entry.metadata().operatorCode());
    });
  }

  @Test
  void shouldFallbackUnknownExternalCodes() {
    FepExternalErrorTaxonomy.TaxonomyEntry actual = FepExternalErrorTaxonomy.resolve("9099");

    assertThat(actual.errorCode()).isEqualTo(ErrorCode.FEP_UNKNOWN_EXTERNAL);
    assertThat(actual.metadata().userMessageKey()).isEqualTo("error.fep.unknown_external");
    assertThat(actual.metadata().operatorCode()).isEqualTo("UNKNOWN_EXTERNAL_9099");
  }
}
