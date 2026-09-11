package com.example.mapchange.config.client;

import com.example.mapchange.common.core.api.ApiResponse;
import com.example.mapchange.common.core.dto.InferenceStatusDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/** analysis-service 客户端（评审 S-01 补位）：capabilities 聚合取推理 provider 状态 */
@FeignClient(name = "analysis-service", path = "/internal/inference", configuration = com.example.mapchange.common.web.config.CommonFeignConfiguration.class)
public interface AnalysisClient {

    @GetMapping("/status")
    ApiResponse<InferenceStatusDto> status();
}
