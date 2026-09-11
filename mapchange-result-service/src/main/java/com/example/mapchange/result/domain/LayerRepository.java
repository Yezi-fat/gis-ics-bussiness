package com.example.mapchange.result.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** layer 表仓储（result-service 独占，schema result） */
public interface LayerRepository extends JpaRepository<LayerEntity, UUID> {

    List<LayerEntity> findByTaskId(UUID taskId);

    void deleteByTaskId(UUID taskId);
}
