package com.fix.fepgateway.entity;

import com.fix.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "fep_replay_cursors")
public class ReplayCursor extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "replay_id", nullable = false, unique = true, length = 36)
  private String replayId;

  @Column(name = "seed", nullable = false, length = 128)
  private String seed;

  @Column(name = "symbol", nullable = false, length = 16)
  private String symbol;

  @Column(name = "cursor_offset", nullable = false)
  private Long cursorOffset;

  @Column(name = "speed_factor", nullable = false, precision = 10, scale = 4)
  private BigDecimal speedFactor;

  @Column(name = "status", nullable = false, length = 16)
  private String status;

  protected ReplayCursor() {
  }

  private ReplayCursor(
      String replayId,
      String seed,
      String symbol,
      Long cursorOffset,
      BigDecimal speedFactor,
      String status
  ) {
    this.replayId = replayId;
    this.seed = seed;
    this.symbol = symbol;
    this.cursorOffset = cursorOffset;
    this.speedFactor = speedFactor;
    this.status = status;
  }

  public static ReplayCursor running(String replayId, String seed, String symbol, BigDecimal speedFactor) {
    return new ReplayCursor(replayId, seed, symbol, 0L, speedFactor, "RUNNING");
  }

  public Long getId() {
    return id;
  }

  public String getReplayId() {
    return replayId;
  }

  public String getSeed() {
    return seed;
  }

  public String getSymbol() {
    return symbol;
  }

  public Long getCursorOffset() {
    return cursorOffset;
  }

  public BigDecimal getSpeedFactor() {
    return speedFactor;
  }

  public String getStatus() {
    return status;
  }

  public void moveTo(Long cursorOffset) {
    this.cursorOffset = cursorOffset;
  }

  public void changeStatus(String status) {
    this.status = status;
  }
}
