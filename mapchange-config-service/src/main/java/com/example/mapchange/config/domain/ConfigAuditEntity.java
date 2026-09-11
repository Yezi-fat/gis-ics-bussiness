package com.example.mapchange.config.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** 配置审计表（FR-10.3：操作人、修改前后值、时间） */
@Entity
@Table(schema = "config", name = "config_audit")
public class ConfigAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String configKey;

    private String oldValue;

    private String newValue;

    private String operator;

    private Instant createdAt;

    protected ConfigAuditEntity() {
    }

    public ConfigAuditEntity(String configKey, String oldValue, String newValue, String operator) {
        this.configKey = configKey;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.operator = operator;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public String getOperator() {
        return operator;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
