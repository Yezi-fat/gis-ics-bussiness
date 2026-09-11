package com.example.mapchange.storage.config;

import com.example.mapchange.common.core.api.BizException;
import com.example.mapchange.common.core.api.ErrorCode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 存储装配与约束（设计 §2.8/§3.4）。
 * 提供方探测已在 StorageProviderEnvironmentPostProcessor（上下文刷新前）完成，
 * 此处输出启动日志与生产约束校验。
 */
@Configuration
public class StorageConfiguration {

    private static final Logger log = LoggerFactory.getLogger(StorageConfiguration.class);

    @Value("${storage.provider:local}")
    private String provider;

    @Value("${storage.local.replicas-check:1}")
    private int expectedReplicas;

    /** 启动日志输出最终提供方（FR-4.4） */
    @PostConstruct
    public void detectProvider() {
        log.info("[storage] final provider = {}（探测见 StorageProviderEnvironmentPostProcessor）", provider);
    }

    /** local 模式生产约束：副本数 > 1 拒绝启动并告警（FR-4.4 约束） */
    @PostConstruct
    public void checkLocalConstraints() {
        if ("local".equals(provider) && expectedReplicas > 1) {
            log.error("[storage] local 模式限单副本（各实例磁盘不共享），当前声明副本数={}", expectedReplicas);
            throw new BizException(ErrorCode.INTERNAL_ERROR,
                    "local 存储模式不支持多副本部署（storage.local.replicas-check=" + expectedReplicas + "）");
        }
    }
}
