package com.example.mapchange.config.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** model_registry 仓储（config-service 独占） */
public interface ModelRegistryRepository extends JpaRepository<ModelRegistryEntity, UUID> {

    Optional<ModelRegistryEntity> findByModelNameAndModelVersion(String modelName, String modelVersion);

    List<ModelRegistryEntity> findByModelType(String modelType);
}
