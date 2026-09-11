package com.example.mapchange.common.web.config;

import com.example.mapchange.common.core.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * config-service 内部读取客户端（common-web 内嵌，供 RemoteConfigCache 回源）。
 * contextId 与服务内业务 ConfigClient 区分，避免 Feign 上下文冲突。
 */
@FeignClient(name = "config-service", path = "/internal/config",
        contextId = "commonConfigServiceInternalClient",
        configuration = CommonFeignConfiguration.class)
public interface ConfigServiceInternalClient {

    /** 按组读取运行配置（full key → value） */
    @GetMapping("/{group}")
    ApiResponse<Map<String, String>> getGroupValues(@PathVariable String group);
}
