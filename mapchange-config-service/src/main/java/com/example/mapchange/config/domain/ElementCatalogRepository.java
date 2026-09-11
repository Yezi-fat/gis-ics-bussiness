package com.example.mapchange.config.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/** element_catalog 仓储（config-service 独占） */
public interface ElementCatalogRepository extends JpaRepository<ElementCatalogEntity, String> {
}
