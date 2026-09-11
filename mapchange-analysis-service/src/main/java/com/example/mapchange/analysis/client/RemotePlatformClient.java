package com.example.mapchange.analysis.client;

import com.example.mapchange.common.core.api.BizException;
import com.example.mapchange.common.core.api.ErrorCode;
import com.example.mapchange.common.core.dto.python.RemoteInferRequest;
import com.example.mapchange.common.core.dto.python.RemoteInferResponse;
import com.example.mapchange.common.web.config.RemoteConfigCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 通用推理服务客户端（完整 URL 直调，需求约束 #10）——不使用 Feign 路径注解假设对方 URL 结构；
 * 以 RestClient 直接 POST 配置的完整端点 inference.remote.endpoint（协议字段按联调规范，FR-5.4）。
 * 超时/重试/熔断由 InferenceRouter 的 Resilience4j 包裹（FR-5.3）。
 */
@Component
public class RemotePlatformClient {

    private static final Logger log = LoggerFactory.getLogger(RemotePlatformClient.class);

    private final RestClient restClient = RestClient.create();
    private final RemoteConfigCache configCache;

    public RemotePlatformClient(RemoteConfigCache configCache) {
        this.configCache = configCache;
    }

    /** POST 完整 endpoint（直调不拼接路径）；endpoint 为空属配置错误 */
    public RemoteInferResponse infer(RemoteInferRequest req) {
        String endpoint = configCache.getOrDefault("inference.remote.endpoint", "");
        if (endpoint.isBlank()) {
            throw new BizException(ErrorCode.INFERENCE_UNAVAILABLE, "远程推理端点未配置");
        }
        try {
            RemoteInferResponse resp = restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(req)
                    .retrieve()
                    .body(RemoteInferResponse.class);
            return resp;
        } catch (org.springframework.web.client.ResourceAccessException e) {
            // 连接失败/读超时 → 超时语义（熔断统计）
            throw new BizException(ErrorCode.INFERENCE_TIMEOUT, "远程推理调用超时/不可达: " + e.getMessage());
        } catch (Exception e) {
            throw new BizException(ErrorCode.INFERENCE_UNAVAILABLE, "远程推理调用失败: " + e.getMessage());
        }
    }
}
