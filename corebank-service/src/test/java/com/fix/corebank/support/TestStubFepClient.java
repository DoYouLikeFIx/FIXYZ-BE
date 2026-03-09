package com.fix.corebank.support;

import com.fix.corebank.client.FepClient;
import com.fix.corebank.client.FepOrderResult;
import com.fix.corebank.client.FepOutboundOrderPayload;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.web.client.RestClient;

public class TestStubFepClient extends FepClient {

  private final AtomicInteger submitCalls = new AtomicInteger();
  private final AtomicInteger queryCalls = new AtomicInteger();
  private volatile FepOrderResult submitResult;
  private volatile FepOrderResult queryResult;
  private volatile RuntimeException submitFailure;
  private volatile RuntimeException queryFailure;

  public TestStubFepClient() {
    super(RestClient.builder(), "http://localhost:65535", "test-internal-secret");
  }

  @Override
  public FepOrderResult submitOrder(FepOutboundOrderPayload payload, String correlationId) {
    submitCalls.incrementAndGet();
    if (submitFailure != null) {
      throw submitFailure;
    }
    return submitResult;
  }

  @Override
  public FepOrderResult queryOrderStatus(String clOrdId, String correlationId) {
    queryCalls.incrementAndGet();
    if (queryFailure != null) {
      throw queryFailure;
    }
    return queryResult;
  }

  public void setSubmitResult(FepOrderResult submitResult) {
    this.submitResult = submitResult;
  }

  public void setQueryResult(FepOrderResult queryResult) {
    this.queryResult = queryResult;
  }

  public void setSubmitFailure(RuntimeException submitFailure) {
    this.submitFailure = submitFailure;
  }

  public void setQueryFailure(RuntimeException queryFailure) {
    this.queryFailure = queryFailure;
  }

  public int submitCalls() {
    return submitCalls.get();
  }

  public int queryCalls() {
    return queryCalls.get();
  }

  public void reset() {
    submitCalls.set(0);
    queryCalls.set(0);
    submitResult = null;
    queryResult = null;
    submitFailure = null;
    queryFailure = null;
  }
}
