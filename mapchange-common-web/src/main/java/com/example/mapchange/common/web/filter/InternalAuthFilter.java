package com.example.mapchange.common.web.filter;

import com.example.mapchange.common.core.api.ApiResponse;
import com.example.mapchange.common.core.api.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 内部服务鉴权（设计 §3.6）：/internal/** 校验 X-Internal-Token（共享密钥，L1 配置注入）。
 * security.auth.enabled=false 时直接放行（FR-10.7）。仅 5 个 Servlet 业务服务注册。
 */
public class InternalAuthFilter extends OncePerRequestFilter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final boolean authEnabled;
    private final String internalToken;

    public InternalAuthFilter(boolean authEnabled, String internalToken) {
        this.authEnabled = authEnabled;
        this.internalToken = internalToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 只管 /internal/**；对外路径的鉴权在 gateway
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!authEnabled) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = request.getHeader("X-Internal-Token");
        if (Objects.equals(internalToken, token)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(ErrorCode.UNAUTHORIZED.defaultHttpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(MAPPER.writeValueAsString(
                ApiResponse.error(ErrorCode.UNAUTHORIZED, "内部接口缺少有效的 X-Internal-Token")));
    }
}
