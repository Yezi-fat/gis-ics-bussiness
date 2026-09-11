package com.example.mapchange.gateway.filter;

import com.example.mapchange.common.core.api.ApiResponse;
import com.example.mapchange.common.core.api.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 全局鉴权过滤器（设计 §2.4/§3.6，FR-10.7，J-038）：
 * - security.auth.enabled=true：校验 JWT（Bearer），解析 sub/role 注入下游
 *   X-User-Id / X-User-Role；管理接口（/api/v1/config/**、/api/v1/models/**）需 ADMIN 角色；
 * - false：不校验令牌，直接放行并注入匿名身份（X-User-Id=anonymous）；
 * - 豁免：/healthz 与 /api/v1/files/**（HMAC 时效 token 下载，不适用 JWT）。
 */
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthGlobalFilter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> ADMIN_PATH_PREFIXES = List.of("/api/v1/config", "/api/v1/models");

    private final boolean authEnabled;
    private final SecretKey key;

    public JwtAuthGlobalFilter(@Value("${security.auth.enabled:true}") boolean authEnabled,
                               @Value("${security.jwt.secret:dev-jwt-secret-key-at-least-32-bytes-long}")
                               String secret) {
        this.authEnabled = authEnabled;
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!authEnabled) {
            return chain.filter(withIdentity(exchange, "anonymous", "ADMIN"));
        }
        if (path.equals("/healthz") || path.startsWith("/api/v1/files/")) {
            return chain.filter(exchange);
        }
        String token = bearer(exchange);
        Claims claims = token != null ? parse(token) : null;
        if (claims == null) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "未认证或令牌无效");
        }
        String userId = claims.getSubject();
        String role = claims.get("role", String.class);
        if (isAdminPath(path) && !"ADMIN".equalsIgnoreCase(role)) {
            return reject(exchange, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "管理接口需 ADMIN 角色");
        }
        return chain.filter(withIdentity(exchange, userId, role));
    }

    private boolean isAdminPath(String path) {
        return ADMIN_PATH_PREFIXES.stream().anyMatch(p -> path.equals(p) || path.startsWith(p + "/"));
    }

    private String bearer(ServerWebExchange exchange) {
        String h = exchange.getRequest().getHeaders().getFirst("Authorization");
        return h != null && h.startsWith("Bearer ") ? h.substring(7) : null;
    }

    private Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (Exception e) {
            log.debug("JWT 校验失败: {}", e.getMessage());
            return null;
        }
    }

    private ServerWebExchange withIdentity(ServerWebExchange exchange, String userId, String role) {
        return exchange.mutate().request(exchange.getRequest().mutate()
                .header("X-User-Id", userId != null ? userId : "anonymous")
                .header("X-User-Role", role != null ? role : "USER")
                .build()).build();
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, ErrorCode code,
                              String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] body = MAPPER.writeValueAsBytes(ApiResponse.error(code, message));
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (Exception e) {
            exchange.getResponse().setStatusCode(status);
            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() {
        return -200;
    }
}
