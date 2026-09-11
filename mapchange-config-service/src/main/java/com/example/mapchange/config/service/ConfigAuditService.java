package com.example.mapchange.config.service;

import com.example.mapchange.config.domain.ConfigAuditEntity;
import com.example.mapchange.config.domain.ConfigAuditRepository;
import org.springframework.stereotype.Service;

/** 配置审计（FR-10.3）：操作人、修改前后值、时间，落 config_audit 表 */
@Service
public class ConfigAuditService {

    private final ConfigAuditRepository repository;

    public ConfigAuditService(ConfigAuditRepository repository) {
        this.repository = repository;
    }

    public void audit(String operator, String key, String oldV, String newV) {
        repository.save(new ConfigAuditEntity(key, oldV, newV, operator));
    }
}
