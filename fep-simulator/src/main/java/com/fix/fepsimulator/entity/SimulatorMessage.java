package com.fix.fepsimulator.entity;

import com.fix.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "simulator_messages")
public class SimulatorMessage extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "connection_id")
  private Long connectionId;

  @Column(name = "message_type", nullable = false, length = 32)
  private String messageType;

  @Column(name = "direction", nullable = false, length = 16)
  private String direction;

  @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
  private String payload;

  protected SimulatorMessage() {
  }

  private SimulatorMessage(Long connectionId, String messageType, String direction, String payload) {
    this.connectionId = connectionId;
    this.messageType = messageType;
    this.direction = direction;
    this.payload = payload;
  }

  public static SimulatorMessage of(Long connectionId, String messageType, String direction, String payload) {
    return new SimulatorMessage(connectionId, messageType, direction, payload);
  }

  public Long getId() {
    return id;
  }

  public Long getConnectionId() {
    return connectionId;
  }

  public String getMessageType() {
    return messageType;
  }

  public String getDirection() {
    return direction;
  }

  public String getPayload() {
    return payload;
  }
}
