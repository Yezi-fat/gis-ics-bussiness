package com.example.mapchange.config.api;

import com.example.mapchange.common.core.api.ApiResponse;
import com.example.mapchange.common.core.dto.CapabilitiesView;
import com.example.mapchange.common.core.dto.ConfigGroupView;
import com.example.mapchange.common.core.dto.ElementDto;
import com.example.mapchange.config.service.CapabilityService;
import com.example.mapchange.config.service.ConfigService;
import com.example.mapchange.config.service.ElementCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 配置/能力/目录对外接口（管理接口经 gateway 鉴权 ADMIN，设计 §2.9）。
 * 操作人取 gateway 注入的 X-User-Id（匿名部署形态为 anonymous，FR-10.7）。
 */
@RestController
@RequestMapping("/api/v1")
public class ConfigController {

    private final ConfigService configService;
    private final ElementCatalogService elementCatalogService;
    private final CapabilityService capabilityService;

    public ConfigController(ConfigService configService, ElementCatalogService elementCatalogService,
                            CapabilityService capabilityService) {
        this.configService = configService;
        this.elementCatalogService = elementCatalogService;
        this.capabilityService = capabilityService;
    }

    /** 按组查看运行配置（含当前值/默认值/是否被修改，FR-10.4） */
    @GetMapping("/config/{group}")
    public ApiResponse<ConfigGroupView> getGroup(@PathVariable String group) {
        return ApiResponse.ok(configService.groupView(group));
    }

    /** 按组修改运行配置（热生效+审计；逐项校验，任一项非法整组拒绝，FR-10.3） */
    @PutMapping("/config/{group}")
    public ApiResponse<Void> updateGroup(@PathVariable String group, @RequestBody Map<String, String> kv,
                                         @RequestHeader(value = "X-User-Id", required = false,
                                                 defaultValue = "anonymous") String operator) {
        configService.update(group, kv, operator);
        return ApiResponse.ok(null);
    }

    /** 恢复该组默认值（FR-10.4） */
    @PostMapping("/config/{group}/reset")
    public ApiResponse<Void> resetGroup(@PathVariable String group,
                                        @RequestHeader(value = "X-User-Id", required = false,
                                                defaultValue = "anonymous") String operator) {
        configService.resetGroup(group, operator);
        return ApiResponse.ok(null);
    }

    /** 系统能力集（FR-10.5/10.6/10.7；前端按能力动态渲染入口） */
    @GetMapping("/capabilities")
    public ApiResponse<CapabilitiesView> capabilities() {
        return ApiResponse.ok(capabilityService.aggregate());
    }

    /** 可识别要素类别目录（FR-6.2；前端渲染选择器，仅启用项） */
    @GetMapping("/elements")
    public ApiResponse<List<ElementDto>> elements() {
        return ApiResponse.ok(elementCatalogService.listEnabled());
    }
}
