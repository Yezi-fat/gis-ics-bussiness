package com.example.mapchange.config.client;

import com.example.mapchange.common.core.api.ApiResponse;
import com.example.mapchange.common.core.dto.SignUrlRequest;
import com.example.mapchange.common.core.dto.SignUrlResponse;
import com.example.mapchange.common.core.dto.StorageUsage;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * storage-service 客户端（评审 R-08/S-01 补位）：
 * 模型登记时上传/读取模型文件（设计 §3.7 下发链路）；capabilities 聚合取存储提供方。
 */
@FeignClient(name = "storage-service", path = "/internal/storage", configuration = com.example.mapchange.common.web.config.CommonFeignConfiguration.class)
public interface StorageClient {

    @PostMapping("/sign-url")
    ApiResponse<SignUrlResponse> signUrl(@RequestBody SignUrlRequest req);

    @GetMapping("/usage")
    ApiResponse<StorageUsage> usage();

    /** 当前存储提供方（capabilities 聚合） */
    @GetMapping("/provider")
    ApiResponse<String> provider();

    /** 读取模型文件（登记时下发共享卷用，设计 §3.7） */
    @GetMapping("/objects/{key}")
    org.springframework.core.io.Resource get(
            @org.springframework.web.bind.annotation.PathVariable("key") String key);
}
