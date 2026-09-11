package com.example.mapchange.gateway.filter;

import com.example.mapchange.gateway.config.GatewayConfigCache;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** RateLimitGlobalFilter 测试（J-038：限流触发返 429） */
class RateLimitGlobalFilterTest {

    @Test
    void rateLimitedReturns429() {
        GatewayConfigCache cache = new GatewayConfigCache();
        cache.putAll(java.util.Map.of("security.rate-limit.sync-per-min", "3"));
        RateLimitGlobalFilter filter = new RateLimitGlobalFilter(cache);
        GatewayFilterChain chain = e -> Mono.empty();

        HttpStatusCode last = null;
        for (int i = 0; i < 4; i++) {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/v1/feature-extraction")
                            .header("X-User-Id", "u1"));
            filter.filter(exchange, chain).block();
            last = exchange.getResponse().getStatusCode();
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, last);
    }

    @Test
    void nonSyncPathNotLimited() {
        GatewayConfigCache cache = new GatewayConfigCache();
        cache.putAll(java.util.Map.of("security.rate-limit.sync-per-min", "1"));
        RateLimitGlobalFilter filter = new RateLimitGlobalFilter(cache);
        GatewayFilterChain chain = e -> Mono.empty();
        for (int i = 0; i < 3; i++) {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/tasks"));
            filter.filter(exchange, chain).block();
            assertNull(exchange.getResponse().getStatusCode());
        }
    }

    @Test
    void differentUsersIndependent() {
        GatewayConfigCache cache = new GatewayConfigCache();
        cache.putAll(java.util.Map.of("security.rate-limit.sync-per-min", "1"));
        RateLimitGlobalFilter filter = new RateLimitGlobalFilter(cache);
        GatewayFilterChain chain = e -> Mono.empty();
        for (String user : new String[]{"u1", "u2"}) {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/v1/feature-extraction")
                            .header("X-User-Id", user));
            filter.filter(exchange, chain).block();
            assertNull(exchange.getResponse().getStatusCode(), user + " 首次请求应放行");
        }
    }
}
