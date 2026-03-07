package com.fix.fepsimulator.repository;

import com.fix.fepsimulator.entity.SimulatorConnection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulatorConnectionRepository extends JpaRepository<SimulatorConnection, Long> {
  Optional<SimulatorConnection> findByConnectionKey(String connectionKey);
}
