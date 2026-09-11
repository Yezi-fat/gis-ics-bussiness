package com.example.mapchange.analysis.api;

import com.example.mapchange.analysis.inference.InferenceProviderResolver;
import com.example.mapchange.common.core.api.ApiResponse;
import com.example.mapchange.common.core.dto.InferenceStatusDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 推理状态内部接口（供 config-service 聚合 capabilities，评审 P-06/S-01）。
 */
@RestController
@RequestMapping("/internal/inference")
public class InferenceInternalController {

    private final InferenceProviderResolver resolver;

    public InferenceInternalController(InferenceProviderResolver resolver) {
        this.resolver = resolver;
    }

    /** 当前推理提供方 + 判定依据（FR-5.6/5.7） */
    @GetMapping("/status")
    public ApiResponse<InferenceStatusDto> status() {
        return ApiResponse.ok(resolver.status());
    }
}
