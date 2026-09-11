package com.example.mapchange.gateway.config;

import org.springframework.context.annotation.Configuration;

/**
 * 按用户限流（设计 §2.4；Resilience4j / Redis 令牌桶二选一，内网单机用内存实现）。
 * 限流值来自 L2 运行配置：gateway 为 WebFlux 栈，不做阻塞式 Feign 回源——
 * 由 GatewayConfigCacheListener 订阅 RabbitMQ 配置广播维护内存缓存（评审 P-05/R-07），
 * 启动初值取 L1 yml。同步接口默认 10 req/min（security.rate-limit 配置组，广播热更新）。
 * TODO M4（J-038）实现。
 */
/**
 * 按用户限流（设计 §2.4；内网单机用内存固定窗口实现，见 filter/RateLimitGlobalFilter）。
 * 限流值来自 L2 运行配置：gateway 为 WebFlux 栈，不做阻塞式 Feign 回源——
 * 由 GatewayConfigCacheListener 订阅 RabbitMQ 配置广播维护内存缓存（评审 P-05/R-07），
 * 启动初值取 L1 yml。同步接口默认 10 req/min（security.rate-limit.sync-per-min，热更新）。
 * 本类为配置归口占位；过滤器实现见 RateLimitGlobalFilter（J-038）。
 */
@Configuration
public class RateLimitConfig {
    // 过滤器 bean 由 RateLimitGlobalFilter（@Component）承载
}
