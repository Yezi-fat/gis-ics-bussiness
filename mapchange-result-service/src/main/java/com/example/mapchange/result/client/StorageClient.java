package com.example.mapchange.result.client;

import com.example.mapchange.common.core.api.ApiResponse;
import com.example.mapchange.common.core.dto.SignUrlRequest;
import com.example.mapchange.common.core.dto.SignUrlResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** storage-service 客户端：regions.geojson 写入、导出文件签名 URL、批量重签（评审 R-01） */
@FeignClient(name = "storage-service", path = "/internal/storage", configuration = com.example.mapchange.common.web.config.CommonFeignConfiguration.class)
public interface StorageClient {

    @PostMapping("/sign-url")
    ApiResponse<SignUrlResponse> signUrl(@RequestBody SignUrlRequest req);

    /** regions.geojson 等结果文件写入 */
    @PutMapping(value = "/objects/{key}", consumes = "application/octet-stream")
    ApiResponse<Void> put(@PathVariable("key") String key, @RequestBody byte[] data);

    /** 读取对象（懒矢量化取蒙版等） */
    @GetMapping("/objects/{key}")
    org.springframework.core.io.Resource get(@PathVariable("key") String key);
}
