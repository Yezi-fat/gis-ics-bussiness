package com.example.mapchange.analysis.client;

import com.example.mapchange.common.core.api.ApiResponse;
import com.example.mapchange.common.core.dto.ElementDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;

/** config-service 客户端：运行配置读取 / 要素目录（Feign + 本地缓存，设计 §3.3） */
@FeignClient(name = "config-service", path = "/internal/config", configuration = com.example.mapchange.common.web.config.CommonFeignConfiguration.class)
public interface ConfigClient {

    /** 按组读取运行配置（RemoteConfigCache 回源用） */
    @GetMapping("/{group}")
    ApiResponse<Map<String, String>> getGroupValues(@PathVariable String group);

    /** 要素类别目录（FR-6.2） */
    @GetMapping("/elements")
    ApiResponse<List<ElementDto>> elementCatalog();
}
