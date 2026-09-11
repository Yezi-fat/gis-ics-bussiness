package com.example.mapchange.gateway.filter;

import com.example.mapchange.common.core.api.ApiResponse;
import com.example.mapchange.common.core.api.ErrorCode;
import com.example.mapchange.gateway.config.GatewayConfigCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 按用户限流（设计 §2.4/§3.6，J-038）：同步接口（feature-extraction / change-detection 的 POST）
 * 按用户令牌桶限流，限流值读 L2 配置 security.rate-limit.sync-per-min（经广播热更新，
 * 评审 P-05：gateway 不做阻塞式 Feign 回源）。
 */
@Component
public class RateLimitGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitGlobalFilter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** 限流仅作用同步分析接口 */
    private static final List<String> SYNC_PATHS =
            List.of("/api/v1/feature-extraction", "/api/v1/change-detection");

    private final GatewayConfigCache configCache;
    /** user → [windowStartEpochSec, count] 固定窗口计数（内网单机足够） */
    private final Map<String, long[]> windows = new ConcurrentHashMap<>();

    public RateLimitGlobalFilter(GatewayConfigCache configCache) {
        this.configCache = configCache;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!"POST".equalsIgnoreCase(exchange.getRequest().getMethod().name())
                || !SYNC_PATHS.contains(path)) {
            return chain.filter(exchange);
        }
        int limit = Integer.parseInt(configCache.getOrDefault("security.rate-limit.sync-per-min", "10"));
        String user = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        if (user == null) {
            user = "anonymous";
        }
        if (!allow(user, limit)) {
            log.warn("限流触发: user={}, path={}, limit={}/min", user, path, limit);
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            try {
                byte[] body = MAPPER.writeValueAsBytes(
                        ApiResponse.error(ErrorCode.RATE_LIMITED, "请求过于频繁，请稍后再试"));
                DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
                return exchange.getResponse().writeWith(Mono.just(buffer));
            } catch (Exception e) {
                return exchange.getResponse().setComplete();
            }
        }
        return chain.filter(exchange);
    }

    /** 固定窗口计数限流 */
    private boolean allow(String user, int limitPerMin) {
        long window = System.currentTimeMillis() / 60000;
        long[] slot = windows.compute(user, (k, v) ->
                (v == null || v[0] != window) ? new long[]{window, 0} : v);
        synchronized (slot) {
            if (slot[1] >= limitPerMin) {
                return false;
            }
            slot[1]++;
            return true;
        }
    }

    @Override
    public int getOrder() {
        return -190;   // 鉴权（-200）之后
    }
}
