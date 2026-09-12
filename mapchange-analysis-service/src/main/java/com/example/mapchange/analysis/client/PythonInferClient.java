package com.example.mapchange.analysis.client;

import com.example.mapchange.common.core.dto.python.ChangeDetectRequest;
import com.example.mapchange.common.core.dto.python.ChangeMaskResponse;
import com.example.mapchange.common.core.dto.python.InferHealthResponse;
import com.example.mapchange.common.core.dto.python.SegmentationRequest;
import com.example.mapchange.common.core.dto.python.SegmentationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Python infer-service 客户端——异步/长超时通道（配置地址直连，不经 Nacos；/infer/* 属内部契约，约束 #10 例外）。
 * 供 TaskWorker 异步链路使用：大图/多期 CPU 推理上限 15min（需求 8.1），read-timeout 由
 * spring.cloud.openfeign client 配置 python-infer 分组下发。
 * X-Inference-Provider 提示（小写连字符 remote/local-gpu/local-cpu，J-01/A-1）；
 * 降级回放用不带提示的 *DefaultProvider 方法（由 infer-service 本地默认配置兜底）。
 */
@FeignClient(name = "python-infer", url = "${python.infer.url}", configuration = com.example.mapchange.common.web.config.CommonFeignConfiguration.class)
public interface PythonInferClient {

    @PostMapping("/infer/segmentation")
    SegmentationResponse segment(@RequestBody SegmentationRequest req,
                                 @RequestHeader("X-Inference-Provider") String providerHint);

    /** 降级回放：不带 provider 提示，由 infer-service 本地默认配置兜底 */
    @PostMapping("/infer/segmentation")
    SegmentationResponse segmentWithDefaultProvider(@RequestBody SegmentationRequest req);

    @PostMapping("/infer/change-detection")
    ChangeMaskResponse detectChange(@RequestBody ChangeDetectRequest req,
                                    @RequestHeader("X-Inference-Provider") String providerHint);

    /** 降级回放：不带 provider 提示 */
    @PostMapping("/infer/change-detection")
    ChangeMaskResponse detectChangeWithDefaultProvider(@RequestBody ChangeDetectRequest req);

    @GetMapping("/health")
    InferHealthResponse health();
}
