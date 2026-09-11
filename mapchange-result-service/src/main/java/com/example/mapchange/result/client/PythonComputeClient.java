package com.example.mapchange.result.client;

import com.example.mapchange.common.core.dto.python.VectorizeRequest;
import com.example.mapchange.common.core.dto.python.VectorizeResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** Python compute-service 客户端（懒矢量化，配置地址直连） */
@FeignClient(name = "python-compute", url = "${python.compute.url}", configuration = com.example.mapchange.common.web.config.CommonFeignConfiguration.class)
public interface PythonComputeClient {

    @PostMapping("/compute/vectorize")
    VectorizeResponse vectorize(@RequestBody VectorizeRequest req);
}
