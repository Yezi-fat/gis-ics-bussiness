package com.example.mapchange.config.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 要素类别目录（FR-6.2；modelClassId 为分割模型输出类别 ID 映射） */
@Entity
@Table(schema = "config", name = "element_catalog")
public class ElementCatalogEntity {

    /** forest / river / residential ...（EuroSAT 10 类，V2 迁移起） */
    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    /** #RRGGBB[AA] */
    @Column(nullable = false)
    private String color;

    @Column(nullable = false)
    private int modelClassId;

    private boolean enabled = true;

    protected ElementCatalogEntity() {
    }

    public ElementCatalogEntity(String id, String name, String color, int modelClassId, boolean enabled) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.modelClassId = modelClassId;
        this.enabled = enabled;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }

    public int getModelClassId() {
        return modelClassId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
