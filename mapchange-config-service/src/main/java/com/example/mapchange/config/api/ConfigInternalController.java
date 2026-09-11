package com.example.mapchange.config.api;

import com.example.mapchange.common.core.api.ApiResponse;
import com.example.mapchange.common.core.dto.ElementDto;
import com.example.mapchange.config.service.ConfigService;
import com.example.mapchange.config.service.ElementCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 配置内部接口（供各服务读取，RemoteConfigCache 回源，设计 §3.3）。
 */
@RestController
@RequestMapping("/internal/config")
public class ConfigInternalController {

    private final ConfigService configService;
    private final ElementCatalogService elementCatalogService;

    public ConfigInternalController(ConfigService configService, ElementCatalogService elementCatalogService) {
        this.configService = configService;
        this.elementCatalogService = elementCatalogService;
    }

    /** 按组读取运行配置（full key → value） */
    @GetMapping("/{group}")
    public ApiResponse<Map<String, String>> getGroupValues(@PathVariable String group) {
        return ApiResponse.ok(configService.groupValues(group));
    }

    /** 要素类别目录 */
    @GetMapping("/elements")
    public ApiResponse<List<ElementDto>> elementCatalog() {
        return ApiResponse.ok(elementCatalogService.listAll());
    }
}
