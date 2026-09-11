package com.example.mapchange.gateway.error;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.autoconfigure.web.reactive.error.DefaultErrorWebExceptionHandler;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * gateway 错误响应整形（评审 S-02，J-038）：gateway 自身产生的 401/403/429/路由 503 等
 * 统一输出 {code, message, task_id, trace_id} 包体；common-web 的 GlobalExceptionHandler
 * 为 Servlet 栈，不适用于 gateway。
 */
@Component
public class GatewayErrorAttributes extends DefaultErrorWebExceptionHandler {

    public GatewayErrorAttributes(ErrorAttributes errorAttributes, WebProperties webProperties,
                                  ApplicationContext applicationContext,
                                  org.springframework.http.codec.ServerCodecConfigurer codecConfigurer) {
        // 自定义 ErrorWebExceptionHandler 会使 Boot 默认错误装配回退，ErrorProperties 无 Bean——
        // 用默认值即可（仅影响默认错误属性字段裁剪）
        super(errorAttributes, webProperties.getResources(),
                new org.springframework.boot.autoconfigure.web.ErrorProperties(), applicationContext);
        // AbstractErrorWebExceptionHandler 要求 messageWriters/messageReaders（WebFlux 错误响应写回通道）
        setMessageWriters(codecConfigurer.getWriters());
        setMessageReaders(codecConfigurer.getReaders());
    }

    @Override
    protected Map<String, Object> getErrorAttributes(ServerRequest request, ErrorAttributeOptions options) {
        Map<String, Object> attrs = super.getErrorAttributes(request, options);
        int status = attrs.get("status") instanceof Number n ? n.intValue() : 500;
        Map<String, Object> unified = new LinkedHashMap<>();
        unified.put("code", codeOf(status));
        unified.put("message", attrs.getOrDefault("error", "gateway error").toString()
                + (attrs.get("message") != null ? ": " + attrs.get("message") : ""));
        unified.put("task_id", null);
        unified.put("trace_id", attrs.get("traceId"));
        return unified;
    }

    private String codeOf(int status) {
        return switch (status) {
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "INVALID_INPUT";
            case 429 -> "RATE_LIMITED";
            case 502, 503 -> "INFERENCE_UNAVAILABLE";
            default -> "INTERNAL_ERROR";
        };
    }
}
