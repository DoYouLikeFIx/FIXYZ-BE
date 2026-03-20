package com.fix.fepgateway.dataplane.marketdata.kis;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

@Service
public class KisApprovalKeyService {

  private final KisApprovalClient kisApprovalClient;
  private final AtomicReference<KisApprovalKey> cachedApprovalKey = new AtomicReference<>();

  public KisApprovalKeyService(KisApprovalClient kisApprovalClient) {
    this.kisApprovalClient = kisApprovalClient;
  }

  public KisApprovalKey currentOrIssue() {
    KisApprovalKey current = cachedApprovalKey.get();
    if (current != null) {
      return current;
    }
    synchronized (this) {
      KisApprovalKey cached = cachedApprovalKey.get();
      if (cached != null) {
        return cached;
      }
      KisApprovalKey issued = kisApprovalClient.issueApprovalKey();
      cachedApprovalKey.set(issued);
      return issued;
    }
  }

  public KisApprovalKey reissue() {
    synchronized (this) {
      KisApprovalKey issued = kisApprovalClient.issueApprovalKey();
      cachedApprovalKey.set(issued);
      return issued;
    }
  }

  public void invalidate() {
    cachedApprovalKey.set(null);
  }
}
