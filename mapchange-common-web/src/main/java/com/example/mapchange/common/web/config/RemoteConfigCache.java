package com.example.mapchange.common.web.config;

import com.example.mapchange.common.core.api.ApiResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 运行配置本地缓存（设计 §3.3，common-web；各业务服务内嵌）。
 * Caffeine 进程内缓存，回源 config-service 内部接口；监听 RabbitMQ fanout（mapchange.config）
 * 广播后刷新（§5.4）。config-service 不可用时沿用本地缓存（可用性设计 §8.3，回源失败记 WARN）。
 */
public class RemoteConfigCache implements ApplicationEventPublisherAware {

    private static final Logger log = LoggerFactory.getLogger(RemoteConfigCache.class);

    private final ConfigServiceInternalClient client;
    private ApplicationEventPublisher eventPublisher;
    /** full key（如 inference.provider）→ value */
    private final Cache<String, String> cache =
            Caffeine.newBuilder().build();
    /** 组加载状态（避免并发重复回源） */
    private final Map<String, Boolean> loadedGroups = new ConcurrentHashMap<>();

    public RemoteConfigCache(ConfigServiceInternalClient client) {
        this.client = client;
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
        this.eventPublisher = publisher;
    }

    /** 读取配置；缓存未命中时回源该组后重查；回源失败返回 null */
    public String get(String key) {
        String v = cache.getIfPresent(key);
        if (v != null) {
            return v;
        }
        int dot = key.indexOf('.');
        if (dot > 0) {
            refreshQuietly(key.substring(0, dot));
        }
        return cache.getIfPresent(key);
    }

    public String getOrDefault(String key, String def) {
        String v = get(key);
        return v != null ? v : def;
    }

    /** 收到配置变更广播后回调 config-service 拉取该组并刷新本地缓存 */
    public void refresh(String group) {
        ApiResponse<Map<String, String>> resp = client.getGroupValues(group);
        if (resp.data() == null) {
            throw new IllegalStateException("config-service 返回空数据: group=" + group);
        }
        resp.data().forEach(cache::put);
        loadedGroups.put(group, Boolean.TRUE);
        log.info("remote config refreshed: group={}, keys={}", group, resp.data().size());
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new ConfigGroupRefreshedEvent(this, group));
        }
    }

    /** 静默刷新（回源失败记 WARN 不抛出，沿用旧缓存） */
    public void refreshQuietly(String group) {
        try {
            refresh(group);
        } catch (Exception e) {
            log.warn("refresh config group {} failed, keep stale cache: {}", group, e.getMessage());
        }
    }
}
