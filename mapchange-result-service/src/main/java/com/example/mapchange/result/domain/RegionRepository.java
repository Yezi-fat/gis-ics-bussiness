package com.example.mapchange.result.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** region 表仓储（result-service 独占，schema result；默认不启用，persist.regions=db 时使用） */
public interface RegionRepository extends JpaRepository<RegionEntity, UUID> {

    List<RegionEntity> findByTaskId(UUID taskId);

    void deleteByTaskId(UUID taskId);
}
