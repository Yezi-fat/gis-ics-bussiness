package com.example.mapchange.common.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 全链路日志（设计 §8.2）：task_id / trace_id 注入 MDC，响应头回写；
 * 跨服务经 HTTP 头 X-Task-Id / X-Trace-Id 透传（含调 Python，Feign 拦截器转发）。
 */
public class TaskIdMdcFilter extends OncePerRequestFilter {

    public static final String HEADER_TASK_ID = "X-Task-Id";
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = headerOrNull(request, HEADER_TRACE_ID);
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
        }
        String taskId = headerOrNull(request, HEADER_TASK_ID);
        try {
            MDC.put("trace_id", traceId);
            if (taskId != null) {
                MDC.put("task_id", taskId);
            }
            response.setHeader(HEADER_TRACE_ID, traceId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("trace_id");
            MDC.remove("task_id");
        }
    }

    private String headerOrNull(HttpServletRequest request, String name) {
        String v = request.getHeader(name);
        return (v == null || v.isBlank()) ? null : v;
    }
}
