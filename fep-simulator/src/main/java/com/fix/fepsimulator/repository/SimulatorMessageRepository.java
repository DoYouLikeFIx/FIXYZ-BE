package com.fix.fepsimulator.repository;

import com.fix.fepsimulator.entity.SimulatorMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulatorMessageRepository extends JpaRepository<SimulatorMessage, Long> {
}
