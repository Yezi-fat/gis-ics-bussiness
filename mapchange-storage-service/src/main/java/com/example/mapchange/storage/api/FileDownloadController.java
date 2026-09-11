package com.example.mapchange.storage.api;

import com.example.mapchange.common.core.api.ApiResponse;
import com.example.mapchange.common.core.api.ErrorCode;
import com.example.mapchange.storage.storage.LocalFileStorageService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件下载（local 模式模拟签名 URL，对外，经 gateway；FR-4.4）。
 * HMAC 时效 token 校验，过期拒绝（401）。
 */
@RestController
public class FileDownloadController {

    private final ObjectProvider<LocalFileStorageService> localStorage;

    public FileDownloadController(ObjectProvider<LocalFileStorageService> localStorage) {
        this.localStorage = localStorage;
    }

    @GetMapping("/api/v1/files/{*key}")
    public ResponseEntity<?> download(@PathVariable String key, @RequestParam String token) {
        LocalFileStorageService storage = localStorage.getIfAvailable();
        if (storage == null) {
            // 非 local 模式本端点不可用（OSS/OBS 由云侧签名 URL 直接访问）
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ErrorCode.INVALID_INPUT, "当前存储提供方非 local"));
        }
        // {*key} 捕获值可能带前导斜杠，统一剥除以与签名口径一致
        String normalizedKey = key.startsWith("/") ? key.substring(1) : key;
        if (!storage.verifyToken(normalizedKey, token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(ErrorCode.UNAUTHORIZED, "下载 token 无效或已过期"));
        }
        byte[] bytes = storage.readBytes(normalizedKey);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }
}
