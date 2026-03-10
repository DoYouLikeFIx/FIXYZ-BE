package com.fix.fepgateway.repository;

import com.fix.fepgateway.entity.GatewaySecurityEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GatewaySecurityEventRepository extends JpaRepository<GatewaySecurityEvent, Long> {
}
