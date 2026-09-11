package com.example.mapchange.config.service;

import com.example.mapchange.common.core.api.BizException;
import com.example.mapchange.common.core.api.ErrorCode;
import com.example.mapchange.common.core.dto.ConfigGroupView;
import com.example.mapchange.config.domain.AppConfigEntity;
import com.example.mapchange.config.domain.AppConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行配置服务（FR-10.1/10.2，设计 §3.3）：app_config 表 + 启动种子写入（仅首次，幂等）；
 * 修改入口唯一：校验 → 写库 → 广播 → 审计。
 */
@Service
public class ConfigService {

    private static final Logger log = LoggerFactory.getLogger(ConfigService.class);

    private final AppConfigRepository repository;
    private final ConfigValidator validator;
    private final ConfigAuditService auditService;
    private final ConfigChangeBroadcaster broadcaster;
    private final Counter configChangeTotal;

    public ConfigService(AppConfigRepository repository, ConfigValidator validator,
                         ConfigAuditService auditService, ConfigChangeBroadcaster broadcaster,
                         MeterRegistry meterRegistry) {
        this.repository = repository;
        this.validator = validator;
        this.auditService = auditService;
        this.broadcaster = broadcaster;
        this.configChangeTotal = Counter.builder("config_change_total")
                .description("运行配置修改次数（FR-10）").register(meterRegistry);
    }

    /** 启动种子写入（仅首次：库中不存在的 key 才写入默认值，幂等） */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedOnStartup() {
        int seeded = 0;
        for (ConfigDefaults.ConfigDef def : ConfigDefaults.ALL) {
            if (!repository.existsById(def.key())) {
                repository.save(new AppConfigEntity(def.key(), def.group(), def.defaultValue(), "system-seed"));
                seeded++;
            }
        }
        log.info("config seed done, seeded={}, total={}", seeded, ConfigDefaults.ALL.size());
    }

    /** 按组读取当前值（full key → value） */
    public Map<String, String> groupValues(String group) {
        requireGroup(group);
        Map<String, String> values = new LinkedHashMap<>();
        for (ConfigDefaults.ConfigDef def : ConfigDefaults.ofGroup(group)) {
            values.put(def.key(), repository.findById(def.key())
                    .map(AppConfigEntity::getValue).orElse(def.defaultValue()));
        }
        return values;
    }

    /** 组视图：当前值/默认值/是否被修改 三态（FR-10.4） */
    public ConfigGroupView groupView(String group) {
        requireGroup(group);
        Map<String, ConfigGroupView.ConfigItem> items = new LinkedHashMap<>();
        for (ConfigDefaults.ConfigDef def : ConfigDefaults.ofGroup(group)) {
            String current = repository.findById(def.key())
                    .map(AppConfigEntity::getValue).orElse(def.defaultValue());
            items.put(def.key(), new ConfigGroupView.ConfigItem(
                    current, def.defaultValue(), !current.equals(def.defaultValue()), null));
        }
        return new ConfigGroupView(group, items);
    }

    /** 修改（热生效）：逐项校验 → 组合校验 → 写库 → 广播 → 审计；任一项非法整组拒绝 */
    @Transactional
    public void update(String group, Map<String, String> kv, String operator) {
        requireGroup(group);
        // 先全量校验（含组合约束），再写库——任一项非法整组不落
        Map<String, String> merged = groupValues(group);
        for (Map.Entry<String, String> e : kv.entrySet()) {
            ConfigDefaults.ConfigDef def = ConfigDefaults.find(e.getKey())
                    .filter(d -> d.group().equals(group))
                    .orElseThrow(() -> new BizException(ErrorCode.INVALID_INPUT,
                            "配置项不属于组 " + group + ": " + e.getKey()));
            validator.validate(def.key(), e.getValue());
            merged.put(def.key(), e.getValue());
        }
        validator.validateGroup(group, merged);
        for (Map.Entry<String, String> e : kv.entrySet()) {
            AppConfigEntity entity = repository.findById(e.getKey())
                    .orElseGet(() -> {
                        ConfigDefaults.ConfigDef def = ConfigDefaults.BY_KEY.get(e.getKey());
                        return new AppConfigEntity(def.key(), def.group(), def.defaultValue(), operator);
                    });
            String oldValue = entity.getValue();
            entity.setValue(e.getValue());
            entity.touch(operator);
            repository.save(entity);
            auditService.audit(operator, e.getKey(), oldValue, e.getValue());
        }
        // 广播必须在事务提交后——否则监听方在提交前回源读到旧值（M4 联调实测缓存永久滞留旧值）
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    broadcaster.broadcast(group);
                }
            });
        } else {
            broadcaster.broadcast(group);
        }
        configChangeTotal.increment();
        log.info("config updated: group={}, keys={}, operator={}", group, kv.keySet(), operator);
    }

    /** 恢复该组默认值（FR-10.4） */
    @Transactional
    public void resetGroup(String group, String operator) {
        requireGroup(group);
        Map<String, String> defaults = new LinkedHashMap<>();
        ConfigDefaults.ofGroup(group).forEach(d -> defaults.put(d.key(), d.defaultValue()));
        update(group, defaults, operator);
    }

    private void requireGroup(String group) {
        if (!ConfigDefaults.groups().contains(group)) {
            throw new BizException(ErrorCode.INVALID_INPUT, "未知配置分组: " + group);
        }
    }
}
