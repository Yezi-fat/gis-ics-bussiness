package com.example.mapchange.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 审计日志过滤器（设计 §2.4/§3.6，J-038）：管理类操作（/api/v1/config/**、/api/v1/models/**
 * 的写请求）记录操作者/路径/时间（结构化日志，FR-10.3 审计链路的入口侧）。
 */
@Component
public class AuditLogGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuditLogGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();
        boolean adminOp = (path.startsWith("/api/v1/config/") || path.startsWith("/api/v1/models"))
                && !"GET".equalsIgnoreCase(method);
        if (adminOp) {
            String operator = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            log.info("audit: operator={}, method={}, path={}", operator != null ? operator : "anonymous",
                    method, path);
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -180;   // 鉴权/限流之后（能拿到注入的用户身份）
    }
}
