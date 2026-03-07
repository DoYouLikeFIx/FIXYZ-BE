package com.fix.fepsimulator.entity;

import com.fix.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "simulator_connections")
public class SimulatorConnection extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "connection_key", nullable = false, unique = true, length = 64)
  private String connectionKey;

  @Column(name = "endpoint", nullable = false, length = 128)
  private String endpoint;

  @Column(name = "status", nullable = false, length = 32)
  private String status;

  protected SimulatorConnection() {
  }

  private SimulatorConnection(String connectionKey, String endpoint, String status) {
    this.connectionKey = connectionKey;
    this.endpoint = endpoint;
    this.status = status;
  }

  public static SimulatorConnection connected(String connectionKey, String endpoint) {
    return new SimulatorConnection(connectionKey, endpoint, "CONNECTED");
  }

  public Long getId() {
    return id;
  }

  public String getConnectionKey() {
    return connectionKey;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public String getStatus() {
    return status;
  }

  public void markStatus(String status) {
    this.status = status;
  }
}
