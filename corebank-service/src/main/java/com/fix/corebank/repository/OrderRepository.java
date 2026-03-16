package com.fix.corebank.repository;

import com.fix.corebank.entity.Order;
import com.fix.corebank.repository.custom.OrderCustomRepository;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long>, OrderCustomRepository {
  Optional<Order> findByClOrdId(String clOrdId);

  Page<Order> findByAccountId(Long accountId, Pageable pageable);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
      update Order o
         set o.status = :status,
             o.externalSyncStatus = :externalSyncStatus,
             o.fepReferenceId = :fepReferenceId,
             o.failureReason = :failureReason,
             o.updatedAt = :updatedAt,
             o.version = o.version + 1
       where o.clOrdId = :clOrdId
         and o.version = :expectedVersion
      """)
  int updateStateIfVersionMatches(
      @Param("clOrdId") String clOrdId,
      @Param("expectedVersion") Long expectedVersion,
      @Param("status") String status,
      @Param("externalSyncStatus") String externalSyncStatus,
      @Param("fepReferenceId") String fepReferenceId,
      @Param("failureReason") String failureReason,
      @Param("updatedAt") Instant updatedAt
  );
}
