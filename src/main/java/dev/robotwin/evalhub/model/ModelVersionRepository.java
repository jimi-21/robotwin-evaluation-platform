package dev.robotwin.evalhub.model;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelVersionRepository extends JpaRepository<ModelVersion, UUID> {

    boolean existsByNameAndVersion(String name, String version);
}
