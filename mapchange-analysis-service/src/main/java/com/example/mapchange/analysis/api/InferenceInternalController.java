package com.example.mapchange.analysis.api;

import com.example.mapchange.analysis.client.PythonInferClient;
import com.example.mapchange.analysis.inference.InferenceProviderResolver;
import com.example.mapchange.common.core.api.ApiResponse;
import com.example.mapchange.common.core.api.BizException;
import com.example.mapchange.common.core.api.ErrorCode;
import com.example.mapchange.common.core.dto.InferenceStatusDto;
import com.example.mapchange.common.core.dto.python.InferModelListResponse;
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
    private final PythonInferClient inferClient;

    public InferenceInternalController(InferenceProviderResolver resolver, PythonInferClient inferClient) {
        this.resolver = resolver;
        this.inferClient = inferClient;
    }

    /** 当前推理提供方 + 判定依据（FR-5.6/5.7）+ GPU 实际能力（infer /health gpu_usable，对接事项 J-3） */
    @GetMapping("/status")
    public ApiResponse<InferenceStatusDto> status() {
        return ApiResponse.ok(resolver.status());
    }

    /** 推理服务实际可用模型清单（透传 infer /infer/models，对接事项 J-2；供模型管理与登记/激活校验） */
    @GetMapping("/models")
    public ApiResponse<InferModelListResponse> models() {
        try {
            return ApiResponse.ok(inferClient.listModels());
        } catch (Exception e) {
            throw new BizException(ErrorCode.INFERENCE_UNAVAILABLE,
                    "infer-service 模型清单获取失败: " + e.getMessage());
        }
    }
}
