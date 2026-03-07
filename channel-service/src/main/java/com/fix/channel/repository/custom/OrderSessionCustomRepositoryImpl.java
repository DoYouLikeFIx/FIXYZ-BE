package com.fix.channel.repository.custom;

import com.fix.channel.entity.OrderSession;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class OrderSessionCustomRepositoryImpl implements OrderSessionCustomRepository {

  @PersistenceContext
  private EntityManager entityManager;

  @Override
  public List<OrderSession> findRecentByMemberId(Long memberId, int limit) {
    return entityManager.createQuery(
            "SELECT os FROM OrderSession os WHERE os.memberId = :memberId ORDER BY os.id DESC",
            OrderSession.class
        )
        .setParameter("memberId", memberId)
        .setMaxResults(Math.max(1, limit))
        .getResultList();
  }
}
