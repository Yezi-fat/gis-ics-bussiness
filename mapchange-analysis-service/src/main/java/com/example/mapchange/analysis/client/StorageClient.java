package com.example.mapchange.analysis.client;

import com.example.mapchange.common.core.api.ApiResponse;
import com.example.mapchange.common.core.dto.SignUrlRequest;
import com.example.mapchange.common.core.dto.SignUrlResponse;
import com.example.mapchange.common.core.dto.StorageUsage;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * storage-service 客户端：put/signUrl/delete/usage。
 * 签名用途：purpose=INTERNAL（供 Python 拉取影像，短 TTL 10min，评审 P-03）/ FRONTEND（默认 2h）。
 * put 路径用 {*key} 保留多级 key 斜杠。
 */
@FeignClient(name = "storage-service", path = "/internal/storage", configuration = com.example.mapchange.common.web.config.CommonFeignConfiguration.class)
public interface StorageClient {

    /** 二进制上传（Feign 不支持 {*key}，用普通路径变量；斜杠保留由 decodeSlash 行为保证） */
    @PutMapping(value = "/objects/{key}", consumes = "application/octet-stream")
    ApiResponse<Void> put(@PathVariable("key") String key, @RequestBody byte[] data);

    /** 签名 URL 签发 */
    @PostMapping("/sign-url")
    ApiResponse<SignUrlResponse> signUrl(@RequestBody SignUrlRequest req);

    /** 批量删除对象 */
    @DeleteMapping("/objects")
    ApiResponse<Void> deleteBatch(@RequestBody List<String> keys);

    @GetMapping("/usage")
    ApiResponse<StorageUsage> usage();
}
