package com.example.mapchange.storage.storage;

import com.example.mapchange.common.web.config.RemoteConfigCache;
import com.example.mapchange.storage.client.TaskClient;
import com.example.mapchange.common.core.dto.CleanupRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 空间保护（FR-4.5，J-032）：每小时检查用量——80%（storage.guard.warn-threshold）告警；
 * 90%（storage.guard.hard-limit）调 task-service 最旧优先清理至目标用量。
 */
@Component
public class StorageUsageGuard {

    private static final Logger log = LoggerFactory.getLogger(StorageUsageGuard.class);

    private final ObjectStorageService storage;
    private final TaskClient taskClient;
    private final RemoteConfigCache configCache;

    public StorageUsageGuard(ObjectStorageService storage, TaskClient taskClient,
                             RemoteConfigCache configCache) {
        this.storage = storage;
        this.taskClient = taskClient;
        this.configCache = configCache;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1M")
    public void check() {
        double ratio;
        try {
            ratio = storage.usage().usageRatio();
        } catch (Exception e) {
            log.warn("存储用量读取失败（本轮跳过）: {}", e.getMessage());
            return;
        }
        double warn = Double.parseDouble(configCache.getOrDefault("storage.guard.warn-threshold", "0.8"));
        double hard = Double.parseDouble(configCache.getOrDefault("storage.guard.hard-limit", "0.9"));
        if (ratio >= hard) {
            log.error("存储用量超过硬上限（{} ≥ {}），触发最旧优先强制清理", ratio, hard);
            taskClient.cleanup(new CleanupRequest(null, hard));
        } else if (ratio >= warn) {
            log.warn("存储用量超过告警阈值（{} ≥ {}）", ratio, warn);
        }
    }
}
