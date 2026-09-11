package com.example.mapchange.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** JwtAuthGlobalFilter 测试（J-038：两态 + 角色控制） */
class JwtAuthGlobalFilterTest {

    private static final String SECRET = "test-jwt-secret-key-at-least-32-bytes!!";

    private String jwt(String sub, String role) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder().subject(sub).claim("role", role)
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(key).compact();
    }

    private record Capture(HttpStatusCode status, ServerWebExchange exchange) {
    }

    private Capture run(JwtAuthGlobalFilter filter, String path, String authHeader) {
        MockServerHttpRequest.BaseBuilder<?> req = MockServerHttpRequest.post(path);
        if (authHeader != null) {
            req.header("Authorization", authHeader);
        }
        MockServerWebExchange exchange = MockServerWebExchange.from(req);
        AtomicReference<ServerWebExchange> seen = new AtomicReference<>();
        GatewayFilterChain chain = e -> {
            seen.set(e);
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        return new Capture(exchange.getResponse().getStatusCode(), seen.get());
    }

    @Test
    void noTokenReturns401() {
        JwtAuthGlobalFilter filter = new JwtAuthGlobalFilter(true, SECRET);
        Capture c = run(filter, "/api/v1/feature-extraction", null);
        assertEquals(HttpStatus.UNAUTHORIZED, c.status());
    }

    @Test
    void validTokenPassesWithIdentity() {
        JwtAuthGlobalFilter filter = new JwtAuthGlobalFilter(true, SECRET);
        Capture c = run(filter, "/api/v1/feature-extraction", "Bearer " + jwt("u1", "USER"));
        assertNull(c.status());   // 未设置状态码 = 放行
        assertEquals("u1", c.exchange().getRequest().getHeaders().getFirst("X-User-Id"));
        assertEquals("USER", c.exchange().getRequest().getHeaders().getFirst("X-User-Role"));
    }

    @Test
    void nonAdminForbiddenOnConfigApi() {
        JwtAuthGlobalFilter filter = new JwtAuthGlobalFilter(true, SECRET);
        Capture c = run(filter, "/api/v1/config/inference", "Bearer " + jwt("u1", "USER"));
        assertEquals(HttpStatus.FORBIDDEN, c.status());
    }

    @Test
    void adminAllowedOnConfigApi() {
        JwtAuthGlobalFilter filter = new JwtAuthGlobalFilter(true, SECRET);
        Capture c = run(filter, "/api/v1/config/inference", "Bearer " + jwt("admin", "ADMIN"));
        assertNull(c.status());
    }

    @Test
    void authDisabledPassesAnonymously() {
        JwtAuthGlobalFilter filter = new JwtAuthGlobalFilter(false, SECRET);
        Capture c = run(filter, "/api/v1/config/inference", null);
        assertNull(c.status());
        assertEquals("anonymous", c.exchange().getRequest().getHeaders().getFirst("X-User-Id"));
    }

    @Test
    void healthzAndFilesSkipAuth() {
        JwtAuthGlobalFilter filter = new JwtAuthGlobalFilter(true, SECRET);
        assertNull(run(filter, "/healthz", null).status());
        assertNull(run(filter, "/api/v1/files/t/m.png", null).status());
    }
}
