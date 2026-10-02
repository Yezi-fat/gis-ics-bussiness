package com.example.mapchange.config.client;

import com.example.mapchange.common.core.api.ApiResponse;
import com.example.mapchange.common.core.dto.InferenceStatusDto;
import com.example.mapchange.common.core.dto.python.InferModelListResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/** analysis-service 客户端（评审 S-01 补位）：capabilities 聚合取推理 provider 状态；模型发现透传（J-2） */
@FeignClient(name = "analysis-service", path = "/internal/inference", configuration = com.example.mapchange.common.web.config.CommonFeignConfiguration.class)
public interface AnalysisClient {

    @GetMapping("/status")
    ApiResponse<InferenceStatusDto> status();

    /** 推理服务实际可用模型清单（analysis 透传 infer /infer/models，对接事项 J-2） */
    @GetMapping("/models")
    ApiResponse<InferModelListResponse> models();
}
