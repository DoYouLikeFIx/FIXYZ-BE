package com.fix.corebank.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fix.common.error.ErrorCode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FepExternalErrorTaxonomyTest {

  private static final Map<String, FepExternalErrorTaxonomy.TaxonomyEntry> EXPECTED = Map.of(
      "9001", FepExternalErrorTaxonomy.TaxonomyEntry.of(
          ErrorCode.CHANNEL_ROUTE_NOT_FOUND,
          "error.channel.route_not_found",
          "NO_ROUTE"
      ),
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
      ),
      "9099", FepExternalErrorTaxonomy.TaxonomyEntry.of(
          ErrorCode.CORE_CONCURRENCY_CONFLICT,
          "error.core.concurrency_conflict",
          "CONCURRENCY_FAILURE"
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
    FepExternalErrorTaxonomy.TaxonomyEntry actual = FepExternalErrorTaxonomy.resolve("9555");

    assertThat(actual.errorCode()).isEqualTo(ErrorCode.FEP_UNKNOWN_EXTERNAL);
    assertThat(actual.metadata().userMessageKey()).isEqualTo("error.fep.unknown_external");
    assertThat(actual.metadata().operatorCode()).isEqualTo("UNKNOWN_EXTERNAL_9555");
  }
}
