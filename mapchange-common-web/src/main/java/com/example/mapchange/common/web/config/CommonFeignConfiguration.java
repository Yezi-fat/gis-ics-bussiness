package com.example.mapchange.common.web.config;

import com.example.mapchange.common.web.feign.PythonErrorDecoder;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import com.example.mapchange.common.web.filter.TaskIdMdcFilter;

/**
 * common-web 内嵌 Feign 客户端的专用配置（经 @FeignClient(configuration=...) 显式绑定，
 * 保证拦截器一定生效）：透传 X-Internal-Token（security.auth.enabled=true 时）与链路头。
 * 注意：本类不注册为全局 Bean，避免被组件扫描应用到全部客户端。
 */
public class CommonFeignConfiguration {

    @Bean
    public RequestInterceptor commonInternalTokenInterceptor(
            @Value("${security.auth.enabled:true}") boolean authEnabled,
            @Value("${security.internal-token:dev-internal-token}") String internalToken) {
        return template -> {
            if (authEnabled) {
                template.header("X-Internal-Token", internalToken);
            }
            String traceId = MDC.get("trace_id");
            if (traceId != null) {
                template.header(TaskIdMdcFilter.HEADER_TRACE_ID, traceId);
            }
            String taskId = MDC.get("task_id");
            if (taskId != null) {
                template.header(TaskIdMdcFilter.HEADER_TASK_ID, taskId);
            }
        };
    }

    /** 统一错误解码（Python 错误码映射 + Java 统一包透传，设计 §6.2，J-026） */
    @Bean
    public ErrorDecoder commonErrorDecoder() {
        return new PythonErrorDecoder();
    }
}
