package com.example.mapchange.config.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/** app_config 仓储（config-service 独占） */
public interface AppConfigRepository extends JpaRepository<AppConfigEntity, String> {
}
