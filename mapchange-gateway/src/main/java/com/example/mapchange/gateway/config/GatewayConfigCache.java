package com.example.mapchange.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * gateway 内存配置缓存（评审 P-05/R-07）：订阅 RabbitMQ 配置广播刷新；
 * WebFlux 栈不做阻塞式 Feign 回源——启动初值取 L1 yml，广播后由监听器标记组脏，
 * 读取方（限流器）取当前值。
 */
@Component
public class GatewayConfigCache {

    private static final Logger log = LoggerFactory.getLogger(GatewayConfigCache.class);

    /** group → 键值（经广播触发回源后写入） */
    private final Map<String, String> values = new ConcurrentHashMap<>();

    public String getOrDefault(String key, String def) {
        return values.getOrDefault(key, def);
    }

    public void putAll(Map<String, String> kv) {
        values.putAll(kv);
        log.info("gateway 配置缓存刷新: keys={}", kv.keySet());
    }
}
