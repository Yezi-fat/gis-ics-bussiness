package com.example.mapchange.storage.api;

import com.example.mapchange.common.core.api.ApiResponse;
import com.example.mapchange.common.core.api.BizException;
import com.example.mapchange.common.core.api.ErrorCode;
import com.example.mapchange.common.core.dto.SignUrlRequest;
import com.example.mapchange.common.core.dto.SignUrlResponse;
import com.example.mapchange.common.core.dto.StorageUsage;
import com.example.mapchange.common.web.config.RemoteConfigCache;
import com.example.mapchange.storage.storage.ObjectStorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 存储内部接口（供各服务调用，设计 §2.8）。
 * 签名 TTL：purpose=INTERNAL（供 Python 拉取影像）默认 10min（storage.sign-url-internal-ttl，
 * 评审 P-03）；其余默认 2h（storage.sign-url-ttl）——均走 L2 运行配置热生效。
 */
@RestController
@RequestMapping("/internal/storage")
public class StorageInternalController {

    private final ObjectStorageService storage;
    private final RemoteConfigCache configCache;
    private final String provider;

    public StorageInternalController(ObjectStorageService storage, RemoteConfigCache configCache,
                                     @org.springframework.beans.factory.annotation.Value(
                                             "${storage.provider:local}") String provider) {
        this.storage = storage;
        this.configCache = configCache;
        this.provider = provider;
    }

    /** 二进制流上传（{*key} 支持多级 key，如 /{taskId}/before.png） */
    @PutMapping("/objects/{*key}")
    public ApiResponse<Void> put(@PathVariable String key, HttpServletRequest body) throws IOException {
        storage.put(key, body.getInputStream(), body.getContentLengthLong(),
                body.getContentType());
        return ApiResponse.ok(null);
    }

    /** 读取对象（内部） */
    @GetMapping("/objects/{*key}")
    public ResponseEntity<Resource> get(@PathVariable String key) {
        return ResponseEntity.ok(new InputStreamResource(storage.get(key)));
    }

    /** 签名 URL 签发 */
    @PostMapping("/sign-url")
    public ApiResponse<SignUrlResponse> signUrl(@RequestBody SignUrlRequest req) {
        boolean internal = "INTERNAL".equalsIgnoreCase(req.purpose());
        Duration ttl = resolveTtl(req.ttl(), internal);
        String url = internal ? storage.signUrlInternal(req.key(), ttl) : storage.signUrl(req.key(), ttl);
        return ApiResponse.ok(new SignUrlResponse(url, System.currentTimeMillis() + ttl.toMillis()));
    }

    /** 批量删除对象（清理编排调用，FR-4.3） */
    @DeleteMapping("/objects")
    public ApiResponse<Void> deleteBatch(@RequestBody List<String> keys) {
        keys.forEach(storage::delete);
        return ApiResponse.ok(null);
    }

    /** 存储用量（FR-4.5） */
    @GetMapping("/usage")
    public ApiResponse<StorageUsage> usage() {
        return ApiResponse.ok(storage.usage());
    }

    /** 当前存储提供方（oss/obs/local；供 capabilities 聚合） */
    @GetMapping("/provider")
    public ApiResponse<String> provider() {
        return ApiResponse.ok(provider);
    }

    private Duration resolveTtl(String ttlParam, boolean internal) {
        if (ttlParam != null && !ttlParam.isBlank()) {
            try {
                return Duration.parse(ttlParam);
            } catch (DateTimeParseException e) {
                throw new BizException(ErrorCode.INVALID_INPUT, "ttl 须为 ISO-8601 时长（如 PT2H）");
            }
        }
        String key = internal ? "storage.sign-url-internal-ttl" : "storage.sign-url-ttl";
        return Duration.parse(configCache.getOrDefault(key, internal ? "PT10M" : "PT2H"));
    }
}
