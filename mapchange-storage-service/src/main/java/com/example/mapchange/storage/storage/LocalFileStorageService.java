package com.example.mapchange.storage.storage;

import com.example.mapchange.common.core.api.BizException;
import com.example.mapchange.common.core.api.ErrorCode;
import com.example.mapchange.common.core.dto.StorageUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * 本地磁盘降级实现（storage.provider=local，默认兜底；FR-4.4）。
 * 卷目录映射 + HMAC 时效 token；签名 URL 形态 {base}/api/v1/files/{key}?token={expireMs}.{hmac}，
 * internal 用途用内网 base（供 Python 直连，评审 P-03），前端用途用公网/gateway base。
 */
@Service
@ConditionalOnProperty(name = "storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements ObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final Path baseDir;
    private final String publicBaseUrl;
    private final String internalBaseUrl;
    private final byte[] hmacSecret;

    public LocalFileStorageService(
            @Value("${storage.local.dir:/data/mapchange}") String dir,
            @Value("${storage.local.public-base-url:http://localhost:8080}") String publicBaseUrl,
            @Value("${storage.local.internal-base-url:http://storage-service:8084}") String internalBaseUrl,
            @Value("${security.internal-token:dev-internal-token}") String internalToken) throws IOException {
        this.baseDir = Path.of(dir).toAbsolutePath().normalize();
        this.publicBaseUrl = stripTrailingSlash(publicBaseUrl);
        this.internalBaseUrl = stripTrailingSlash(internalBaseUrl);
        this.hmacSecret = internalToken.getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(baseDir);
        log.info("local storage initialized, baseDir={}", baseDir);
    }

    @Override
    public void put(String key, InputStream in, long size, String contentType) {
        Path target = resolveSafe(key);
        try {
            Files.createDirectories(target.getParent());
            Path tmp = Files.createTempFile(baseDir, ".upload-", ".tmp");
            try (in) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "写入本地存储失败: " + key);
        }
    }

    @Override
    public InputStream get(String key) {
        try {
            return Files.newInputStream(resolveSafe(key));
        } catch (IOException e) {
            throw new BizException(ErrorCode.INVALID_INPUT, "对象不存在或不可读: " + key);
        }
    }

    @Override
    public String signUrl(String key, Duration ttl) {
        return signUrl(key, ttl, false);
    }

    /** purpose=INTERNAL：内网 base（Python 直连，评审 P-03） */
    @Override
    public String signUrlInternal(String key, Duration ttl) {
        return signUrl(key, ttl, true);
    }

    /** purpose=INTERNAL 时用内网 base（Python 直连）；否则用对前端可达的 public base */
    public String signUrl(String key, Duration ttl, boolean internal) {
        // 统一剥除前导斜杠——URL 拼接与下载端 {*key} 捕获/验签同口径
        String k = key.startsWith("/") ? key.substring(1) : key;
        long expireMs = System.currentTimeMillis() + ttl.toMillis();
        String token = expireMs + "." + hmac(k, expireMs);
        String base = internal ? internalBaseUrl : publicBaseUrl;
        // key 为系统生成的安全字符集（字母/数字/斜杠/点/横线），不整体 URL 编码
        // （编码 %2F 会被 Tomcat/gateway 默认拒绝）
        return base + "/api/v1/files/" + k + "?token=" + token;
    }

    /** 校验下载 token（FileDownloadController 用）；合法返回 true */
    public boolean verifyToken(String key, String token) {
        if (token == null) {
            return false;
        }
        int dot = token.indexOf('.');
        if (dot <= 0) {
            return false;
        }
        try {
            long expireMs = Long.parseLong(token.substring(0, dot));
            if (System.currentTimeMillis() > expireMs) {
                return false;
            }
            return hmac(key, expireMs).equals(token.substring(dot + 1));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** 读取对象字节（校验过 token 后由 Controller 调用） */
    public byte[] readBytes(String key) {
        try {
            return Files.readAllBytes(resolveSafe(key));
        } catch (IOException e) {
            throw new BizException(ErrorCode.INVALID_INPUT, "对象不存在或不可读: " + key);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolveSafe(key));
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "删除本地对象失败: " + key);
        }
    }

    @Override
    public StorageUsage usage() {
        try {
            var store = Files.getFileStore(baseDir);
            long total = store.getTotalSpace();
            long used = total - store.getUsableSpace();
            return new StorageUsage(used, total, total == 0 ? 0 : (double) used / total);
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "读取磁盘用量失败");
        }
    }

    /** 防路径穿越：拒绝绝对路径与 .. 段 */
    private Path resolveSafe(String key) {
        if (key == null || key.isBlank() || key.contains("..")) {
            throw new BizException(ErrorCode.INVALID_INPUT, "非法存储 key: " + key);
        }
        Path resolved = baseDir.resolve(key.startsWith("/") ? key.substring(1) : key).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new BizException(ErrorCode.INVALID_INPUT, "非法存储 key: " + key);
        }
        return resolved;
    }

    private String hmac(String key, long expireMs) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(hmacSecret, HMAC_ALGORITHM));
            byte[] digest = mac.doFinal((key + ":" + expireMs).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
