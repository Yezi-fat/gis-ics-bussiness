package com.example.mapchange.storage.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * storage.provider=auto 探测（FR-4.4，J-018a）：在上下文刷新前解析最终提供方——
 * OSS/OBS 配置完整（endpoint 非空）→ 用之，否则 local；结果写入高优先级属性源，
 * 供 @ConditionalOnProperty 装配三实现；启动日志输出最终提供方。
 * 注：连通性探测在 J-018a 实现装配时完善（本版按配置完整性判定）。
 */
public class StorageProviderEnvironmentPostProcessor implements EnvironmentPostProcessor {

    /** 解析结果，供 /internal/storage/provider 端点读取 */
    public static final String RESOLVED_KEY = "storage.provider.resolved";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String configured = environment.getProperty("storage.provider", "auto");
        String resolved = switch (configured) {
            case "oss", "obs", "local" -> configured;
            default -> {
                String ossEndpoint = environment.getProperty("storage.oss.endpoint", "");
                String obsEndpoint = environment.getProperty("storage.obs.endpoint", "");
                if (!ossEndpoint.isBlank()) {
                    yield "oss";
                }
                if (!obsEndpoint.isBlank()) {
                    yield "obs";
                }
                yield "local";
            }
        };
        Map<String, Object> props = new HashMap<>();
        props.put("storage.provider", resolved);
        props.put(RESOLVED_KEY, resolved);
        environment.getPropertySources().addFirst(new MapPropertySource("storageProviderResolution", props));
        System.out.printf("[storage] provider resolved: configured=%s -> %s%n", configured, resolved);
    }
}
