package com.example.mapchange.analysis.client;

import com.example.mapchange.common.core.dto.python.SegmentationRequest;
import com.example.mapchange.common.core.dto.python.SegmentationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Python infer-service 客户端——同步/短超时通道（与 {@link PythonInferClient} 同地址、同契约）。
 * 供 Web 线程同步小图链路使用（长边 ≤ inference.sync-max-input-size）：超时分档（J-09/B-2）——
 * 同步短超时快速失败转错误，异步长超时覆盖 CPU 大图推理；两通道 read-timeout 独立配置。
 */
@FeignClient(name = "python-infer-sync", url = "${python.infer.url}", configuration = com.example.mapchange.common.web.config.CommonFeignConfiguration.class)
public interface PythonInferSyncClient {

    @PostMapping("/infer/segmentation")
    SegmentationResponse segment(@RequestBody SegmentationRequest req,
                                 @RequestHeader("X-Inference-Provider") String providerHint);
}
