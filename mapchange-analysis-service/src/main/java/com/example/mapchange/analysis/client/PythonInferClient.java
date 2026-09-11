package com.example.mapchange.analysis.client;

import com.example.mapchange.common.core.dto.python.ChangeDetectRequest;
import com.example.mapchange.common.core.dto.python.ChangeMaskResponse;
import com.example.mapchange.common.core.dto.python.PythonHealthResponse;
import com.example.mapchange.common.core.dto.python.SegmentationRequest;
import com.example.mapchange.common.core.dto.python.SegmentationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Python infer-service 客户端（配置地址直连，不经 Nacos；路径 /infer/* 属内部契约，约束 #10 例外）。
 * LOCAL_GPU/LOCAL_CPU 请求头带 X-Inference-Provider 提示（设计 §3.2）。
 */
@FeignClient(name = "python-infer", url = "${python.infer.url}", configuration = com.example.mapchange.common.web.config.CommonFeignConfiguration.class)
public interface PythonInferClient {

    @PostMapping("/infer/segmentation")
    SegmentationResponse segment(@RequestBody SegmentationRequest req);

    @PostMapping("/infer/change-detection")
    ChangeMaskResponse detectChange(@RequestBody ChangeDetectRequest req);

    @GetMapping("/health")
    PythonHealthResponse health();
}
