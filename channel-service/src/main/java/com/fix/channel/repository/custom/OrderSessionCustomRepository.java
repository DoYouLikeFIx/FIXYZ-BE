package com.fix.channel.repository.custom;

import com.fix.channel.entity.OrderSession;
import java.util.List;

public interface OrderSessionCustomRepository {
  List<OrderSession> findRecentByMemberId(Long memberId, int limit);
}
