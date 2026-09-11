package com.example.mapchange.common.web;

import com.example.mapchange.common.web.api.GlobalExceptionHandler;
import com.example.mapchange.common.web.config.ConfigBroadcastListener;
import com.example.mapchange.common.web.config.ConfigServiceInternalClient;
import com.example.mapchange.common.web.config.RemoteConfigCache;
import com.example.mapchange.common.web.filter.InternalAuthFilter;
import com.example.mapchange.common.web.filter.TaskIdMdcFilter;
import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;

import java.util.List;

/**
 * common-web 自动装配（仅 5 个 Servlet 业务服务生效，gateway 为 WebFlux 不依赖本模块）：
 * 内部令牌 Filter、MDC Filter、全局异常处理、RemoteConfigCache + 配置广播监听、
 * Feign 内部令牌与链路头透传拦截器。
 */
@AutoConfiguration
@EnableFeignClients(clients = ConfigServiceInternalClient.class)
public class CommonWebAutoConfiguration {

    private final boolean authEnabled;
    private final String internalToken;
    private final RemoteConfigCache remoteConfigCache;
    private final List<String> warmupGroups;

    public CommonWebAutoConfiguration(
            @Value("${security.auth.enabled:true}") boolean authEnabled,
            @Value("${security.internal-token:dev-internal-token}") String internalToken,
            ConfigServiceInternalClient configServiceInternalClient,
            @Value("${mapchange.config.warmup-groups:inference,task,persist,storage,feature,security,cache,features}")
            List<String> warmupGroups) {
        this.authEnabled = authEnabled;
        this.internalToken = internalToken;
        this.remoteConfigCache = new RemoteConfigCache(configServiceInternalClient);
        this.warmupGroups = warmupGroups;
    }

    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    /** 内部接口鉴权（/internal/** 校验 X-Internal-Token；auth.enabled=false 时放行，FR-10.7） */
    @Bean
    public FilterRegistrationBean<InternalAuthFilter> internalAuthFilter() {
        FilterRegistrationBean<InternalAuthFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new InternalAuthFilter(authEnabled, internalToken));
        bean.setOrder(-100);
        return bean;
    }

    /** task_id / trace_id 注入 MDC 并透传 */
    @Bean
    public FilterRegistrationBean<TaskIdMdcFilter> taskIdMdcFilter() {
        FilterRegistrationBean<TaskIdMdcFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new TaskIdMdcFilter());
        bean.setOrder(-101);
        return bean;
    }

    /** Feign 调用透传内部令牌与链路头（X-Internal-Token / X-Trace-Id / X-Task-Id） */
    @Bean
    public RequestInterceptor internalFeignInterceptor() {
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

    @Bean
    public RemoteConfigCache remoteConfigCache() {
        return remoteConfigCache;
    }

    @Bean
    @ConditionalOnClass(name = "org.springframework.amqp.rabbit.annotation.RabbitListener")
    public ConfigBroadcastListener configBroadcastListener() {
        return new ConfigBroadcastListener(remoteConfigCache);
    }

    /**
     * RabbitMQ 消息统一用 JSON 序列化（record 契约，如 TaskMessage）；
     * Boot 自动装配会将其应用于 RabbitTemplate 与监听容器——配置广播的组名以 JSON 字符串传输。
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.amqp.rabbit.core.RabbitTemplate")
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /** 启动预热：按组回源（失败静默，运行期广播会再刷新；config-service 未就绪不阻塞启动，评审 T-04） */
    @EventListener(ApplicationReadyEvent.class)
    public void warmup() {
        warmupGroups.forEach(remoteConfigCache::refreshQuietly);
    }
}
