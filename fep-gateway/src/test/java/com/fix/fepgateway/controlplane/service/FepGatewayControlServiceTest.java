package com.fix.fepgateway.controlplane.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fix.common.fep.FepOrdStatus;
import com.fix.fepgateway.dataplane.fix.FixDataPlaneService;
import com.fix.fepgateway.entity.GatewayOrder;
import com.fix.fepgateway.repository.GatewayOrderRepository;
import com.fix.fepgateway.vo.GatewayOrderResult;
import com.fix.fepgateway.vo.GatewayOrderStatusCommand;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FepGatewayControlServiceTest {

  private static final String CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174301";

  @Test
  void shouldDelegateStatusLookupToFixDataPlane() {
    GatewayOrder order = GatewayOrder.received(
        CL_ORD_ID,
        "ACC-001",
        "ref-301",
        Instant.parse("2026-03-10T00:10:00Z"),
        "005930",
        "BUY",
        BigDecimal.TEN,
        "LIMIT",
        72000L,
        "FIX"
    );
    GatewayOrderResult expected = new GatewayOrderResult(
        CL_ORD_ID,
        "FEP-KRX-" + CL_ORD_ID,
        null,
        FepOrdStatus.FILLED,
        10L,
        72000L,
        0L,
        Instant.parse("2026-03-10T00:00:00Z"),
        Instant.parse("2026-03-10T00:00:05Z"),
        null,
        null,
        null,
        null
    );
    RecordingFixDataPlaneService fixDataPlaneService = new RecordingFixDataPlaneService(expected);
    FepGatewayControlService service = new FepGatewayControlService(
        repositoryReturning(Optional.of(order)),
        null,
        null,
        null,
        fixDataPlaneService
    );

    GatewayOrderResult actual = service.status(GatewayOrderStatusCommand.of(CL_ORD_ID));

    assertThat(actual).isEqualTo(expected);
    assertThat(fixDataPlaneService.lastClOrdId).isEqualTo(CL_ORD_ID);
    assertThat(fixDataPlaneService.lastOrder).isSameAs(order);
  }

  @Test
  void shouldDelegateMissingStatusLookupToFixDataPlaneAsUnknown() {
    GatewayOrderResult expected = new GatewayOrderResult(
        CL_ORD_ID,
        null,
        null,
        FepOrdStatus.UNKNOWN,
        null,
        null,
        null,
        null,
        Instant.parse("2026-03-10T00:00:05Z"),
        "external system does not have a matching order",
        null,
        null,
        null
    );
    RecordingFixDataPlaneService fixDataPlaneService = new RecordingFixDataPlaneService(expected);
    FepGatewayControlService service = new FepGatewayControlService(
        repositoryReturning(Optional.empty()),
        null,
        null,
        null,
        fixDataPlaneService
    );

    GatewayOrderResult actual = service.status(GatewayOrderStatusCommand.of(CL_ORD_ID));

    assertThat(actual).isEqualTo(expected);
    assertThat(fixDataPlaneService.lastClOrdId).isEqualTo(CL_ORD_ID);
    assertThat(fixDataPlaneService.lastOrder).isNull();
  }

  private GatewayOrderRepository repositoryReturning(Optional<GatewayOrder> lookupResult) {
    return (GatewayOrderRepository) Proxy.newProxyInstance(
        GatewayOrderRepository.class.getClassLoader(),
        new Class<?>[] {GatewayOrderRepository.class},
        (proxy, method, args) -> {
          if ("findByClOrdId".equals(method.getName())) {
            return lookupResult;
          }
          if ("toString".equals(method.getName())) {
            return "GatewayOrderRepositoryProxy";
          }
          throw new UnsupportedOperationException(method.getName());
        }
    );
  }

  private static final class RecordingFixDataPlaneService extends FixDataPlaneService {

    private final GatewayOrderResult response;
    private String lastClOrdId;
    private GatewayOrder lastOrder;

    private RecordingFixDataPlaneService(GatewayOrderResult response) {
      this.response = response;
    }

    @Override
    public GatewayOrderResult sendOrderStatusRequest(String clOrdId, GatewayOrder order) {
      this.lastClOrdId = clOrdId;
      this.lastOrder = order;
      return response;
    }
  }
}
