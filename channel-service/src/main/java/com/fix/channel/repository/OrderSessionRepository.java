package com.fix.channel.repository;

import com.fix.channel.entity.OrderSession;
import com.fix.channel.entity.OrderSessionStatus;
import com.fix.channel.repository.custom.OrderSessionCustomRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderSessionRepository extends JpaRepository<OrderSession, Long>, OrderSessionCustomRepository {
  Optional<OrderSession> findByOrderSessionId(String orderSessionId);

  Optional<OrderSession> findByClOrdId(String clOrdId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select os from OrderSession os where os.clOrdId = :clOrdId")
  Optional<OrderSession> findByClOrdIdForUpdate(@Param("clOrdId") String clOrdId);

  long deleteByOrderSessionId(String orderSessionId);

  List<OrderSession> findByStatusInAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
      Collection<OrderSessionStatus> statuses,
      Instant referenceTime,
      Pageable pageable
  );

  List<OrderSession> findByStatusAndExecutingStartedAtLessThanEqualOrderByExecutingStartedAtAsc(
      OrderSessionStatus status,
      Instant referenceTime,
      Pageable pageable
  );

  List<OrderSession> findByStatusAndExecutingStartedAtIsNullAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(
      OrderSessionStatus status,
      Instant referenceTime,
      Pageable pageable
  );

  List<OrderSession> findByStatusOrderByUpdatedAtAscOrderSessionIdAsc(OrderSessionStatus status, Pageable pageable);

  long countByStatusIn(Collection<OrderSessionStatus> statuses);

  Optional<OrderSession> findTopByOrderByUpdatedAtDesc();

  Optional<OrderSession> findTopByStatusInOrderByUpdatedAtDescIdDesc(Collection<OrderSessionStatus> statuses);

  @Query("""
      SELECT os
      FROM OrderSession os
      WHERE os.status = :status
      ORDER BY COALESCE(os.executedAt, os.updatedAt) DESC, os.id DESC
      """)
  List<OrderSession> findByStatusOrderByEffectiveExecutionTimestampDesc(
      @Param("status") OrderSessionStatus status,
      Pageable pageable
  );

  @Query("""
      SELECT os
      FROM OrderSession os
      WHERE os.status = :status
        AND (
          os.recoveryNextAttemptAt IS NULL
          OR os.recoveryNextAttemptAt <= :eligibleAt
        )
        AND (
          :updatedAtCursor IS NULL
          OR os.updatedAt > :updatedAtCursor
          OR (os.updatedAt = :updatedAtCursor AND os.orderSessionId > :orderSessionIdCursor)
        )
      ORDER BY os.updatedAt ASC, os.orderSessionId ASC
      """)
  List<OrderSession> findByStatusAfterUpdatedAtCursorOrderByUpdatedAtAscOrderSessionIdAsc(
      @Param("status") OrderSessionStatus status,
      @Param("eligibleAt") Instant eligibleAt,
      @Param("updatedAtCursor") Instant updatedAtCursor,
      @Param("orderSessionIdCursor") String orderSessionIdCursor,
      Pageable pageable
  );

  @Query("""
      SELECT os
      FROM OrderSession os
      WHERE os.status = :status
        AND (
          os.recoveryNextAttemptAt IS NULL
          OR os.recoveryNextAttemptAt <= :eligibleAt
        )
      ORDER BY os.updatedAt ASC, os.orderSessionId ASC
      """)
  List<OrderSession> findEligibleByStatusOrderByUpdatedAtAscOrderSessionIdAsc(
      @Param("status") OrderSessionStatus status,
      @Param("eligibleAt") Instant eligibleAt,
      Pageable pageable
  );
}
