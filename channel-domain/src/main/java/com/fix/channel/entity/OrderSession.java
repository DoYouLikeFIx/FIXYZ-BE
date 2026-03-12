package com.fix.channel.entity;
import com.fix.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "order_sessions")
public class OrderSession extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "order_session_id", nullable = false, unique = true, length = 36, columnDefinition = "CHAR(36)")
  private String orderSessionId;

  @Column(name = "member_id", nullable = false)
  private Long memberId;

  @Column(name = "account_id")
  private Long accountId;

  @Column(name = "cl_ord_id", nullable = false, unique = true, length = 64)
  private String clOrdId;

  // Legacy order_ref column now stores a deterministic replay fingerprint.
  @Column(name = "order_ref", nullable = false, length = 64)
  private String replayFingerprint;

  @Column(name = "symbol", length = 16)
  private String symbol;

  @Column(name = "side", length = 16)
  private String side;

  @Column(name = "order_type", length = 16)
  private String orderType;

  @Column(name = "qty", precision = 19, scale = 4)
  private BigDecimal qty;

  @Column(name = "price", precision = 19, scale = 4)
  private BigDecimal price;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private OrderSessionStatus status;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  protected OrderSession() {
  }

  private OrderSession(
      Long memberId,
      Long accountId,
      String clOrdId,
      String replayFingerprint,
      String symbol,
      String side,
      String orderType,
      BigDecimal qty,
      BigDecimal price,
      OrderSessionStatus status,
      Instant expiresAt
  ) {
    this.orderSessionId = UUID.randomUUID().toString();
    this.memberId = memberId;
    this.accountId = accountId;
    this.clOrdId = clOrdId;
    this.replayFingerprint = replayFingerprint;
    this.symbol = symbol;
    this.side = side;
    this.orderType = orderType;
    this.qty = qty;
    this.price = price;
    this.status = status;
    this.expiresAt = expiresAt;
  }

  public static OrderSession pendingNew(
      Long memberId,
      Long accountId,
      String clOrdId,
      String replayFingerprint,
      String symbol,
      String side,
      String orderType,
      BigDecimal qty,
      BigDecimal price,
      Instant expiresAt
  ) {
    return new OrderSession(
        memberId,
        accountId,
        clOrdId,
        replayFingerprint,
        symbol,
        side,
        orderType,
        qty,
        price,
        OrderSessionStatus.PENDING_NEW,
        expiresAt
    );
  }

  public Long getId() {
    return id;
  }

  public String getOrderSessionId() {
    return orderSessionId;
  }

  public Long getMemberId() {
    return memberId;
  }

  public Long getAccountId() {
    return accountId;
  }

  public String getClOrdId() {
    return clOrdId;
  }

  public String getReplayFingerprint() {
    return replayFingerprint;
  }

  public String getSymbol() {
    return symbol;
  }

  public String getSide() {
    return side;
  }

  public String getOrderType() {
    return orderType;
  }

  public BigDecimal getQty() {
    return qty;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public OrderSessionStatus getStatus() {
    return status;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public boolean ownedBy(Long memberId) {
    return Objects.equals(this.memberId, memberId);
  }

  public boolean matchesReplayFingerprint(String candidateFingerprint) {
    return Objects.equals(this.replayFingerprint, candidateFingerprint);
  }

  public void expire() {
    this.status = OrderSessionStatus.EXPIRED;
  }
}
