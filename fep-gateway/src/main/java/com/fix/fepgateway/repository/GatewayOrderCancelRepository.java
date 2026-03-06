package com.fix.fepgateway.repository;

import com.fix.fepgateway.entity.GatewayOrderCancel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GatewayOrderCancelRepository extends JpaRepository<GatewayOrderCancel, Long> {
}
