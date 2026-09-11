package com.example.mapchange.storage.config;

import com.example.mapchange.storage.storage.ObjectStorageService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 存储指标（设计 §8.2，J-035）：storage_usage_ratio（FR-4.5 用量监控） */
@Configuration
public class StorageMetricsConfig {

    @Bean
    public Gauge storageUsageRatio(MeterRegistry registry, ObjectStorageService storage) {
        return Gauge.builder("storage_usage_ratio", () -> {
                    try {
                        return storage.usage().usageRatio();
                    } catch (Exception e) {
                        return Double.NaN;
                    }
                }).description("存储用量比").register(registry);
    }
}
