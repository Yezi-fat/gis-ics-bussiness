package com.example.mapchange.config.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/** config_audit 仓储（config-service 独占） */
public interface ConfigAuditRepository extends JpaRepository<ConfigAuditEntity, Long> {
}
