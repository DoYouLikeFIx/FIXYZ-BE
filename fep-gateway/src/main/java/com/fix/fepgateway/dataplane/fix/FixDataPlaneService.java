package com.fix.fepgateway.dataplane.fix;

import com.fix.fepgateway.vo.GatewayOrderCancelCommand;
import com.fix.fepgateway.vo.GatewayOrderReplayCommand;
import com.fix.fepgateway.vo.GatewayOrderSubmitCommand;
import org.springframework.stereotype.Service;

@Service
public class FixDataPlaneService {

  public String sendNewOrder(GatewayOrderSubmitCommand command) {
    return "FIX_ACCEPTED";
  }

  public String sendCancel(GatewayOrderCancelCommand command) {
    return "FIX_CANCEL_ACCEPTED";
  }

  public String sendReplay(GatewayOrderReplayCommand command) {
    return "FIX_REPLAY_ACCEPTED";
  }
}
