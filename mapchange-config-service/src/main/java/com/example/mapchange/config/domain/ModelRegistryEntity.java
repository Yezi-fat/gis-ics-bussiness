package com.example.mapchange.config.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * 本地模型版本注册表（FR-3.5，评审 P-02）：Java 侧只做登记与激活，
 * 文件本体由 infer-service 从共享卷加载（设计 §3.7）。
 */
@Entity
@Table(schema = "config", name = "model_registry",
        uniqueConstraints = @UniqueConstraint(columnNames = {"model_name", "model_version"}))
public class ModelRegistryEntity {

    @Id
    private UUID id;

    /** landcover-seg / change-detection */
    @Column(name = "model_name", nullable = false)
    private String modelName;

    @Column(name = "model_version", nullable = false)
    private String modelVersion;

    /** SEG / CHANGE */
    @Column(name = "model_type", nullable = false)
    private String modelType;

    /** 模型文件（.onnx）存储位置 */
    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    /** REGISTERED / ACTIVE / RETIRED */
    @Column(nullable = false)
    private String status;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    protected ModelRegistryEntity() {
    }

    public ModelRegistryEntity(String modelName, String modelVersion, String modelType,
                               String storageKey, String createdBy) {
        this.id = UUID.randomUUID();
        this.modelName = modelName;
        this.modelVersion = modelVersion;
        this.modelType = modelType;
        this.storageKey = storageKey;
        this.status = "REGISTERED";
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getModelName() {
        return modelName;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public String getModelType() {
        return modelType;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(Instant activatedAt) {
        this.activatedAt = activatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
