package com.example.mapchange.config.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** 运行配置表（schema config，FR-10.2 持久化载体；config-service 独占写入） */
@Entity
@Table(schema = "config", name = "app_config")
public class AppConfigEntity {

    /** 如 inference.provider */
    @Id
    private String configKey;

    /** inference / task / storage / persist / feature / security / cache / features */
    @Column(nullable = false)
    private String configGroup;

    @Column(nullable = false)
    private String value;

    private String updatedBy;

    private Instant updatedAt;

    protected AppConfigEntity() {
    }

    public AppConfigEntity(String configKey, String configGroup, String value, String updatedBy) {
        this.configKey = configKey;
        this.configGroup = configGroup;
        this.value = value;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getConfigGroup() {
        return configGroup;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void touch(String operator) {
        this.updatedBy = operator;
        this.updatedAt = Instant.now();
    }
}
