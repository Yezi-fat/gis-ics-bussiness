package com.example.mapchange.analysis.inference;

import com.example.mapchange.common.core.dto.python.PythonHealthResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 环境探测（FR-5.6/5.7）：远程端点连通性 + infer-service /health（GPU/CPU 能力上报）。
 */
@Component
public class EnvironmentProbe {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentProbe.class);

    private final RestClient restClient = RestClient.create();
    private final com.example.mapchange.analysis.client.PythonInferClient inferClient;

    public EnvironmentProbe(com.example.mapchange.analysis.client.PythonInferClient inferClient) {
        this.inferClient = inferClient;
    }

    /** 连通性探测（收到任何 HTTP 响应即视为可达）；未配置返回 false 且记 INFO（FR-5.6 正常形态） */
    public boolean checkRemote(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            log.info("未配置远程推理服务（remote.endpoint 为空）——正常部署形态，继续探测本地环境");
            return false;
        }
        try {
            restClient.get().uri(endpoint).retrieve().toBodilessEntity();
            log.info("远程推理服务连通性探测通过: {}", endpoint);
            return true;
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.warn("远程推理服务不可达: {} ({})", endpoint, e.getMessage());
            return false;
        } catch (Exception e) {
            // 4xx/5xx 说明网络可达
            log.info("远程推理服务可达（HTTP 错误响应属可达）: {} ({})", endpoint, e.getMessage());
            return true;
        }
    }

    /** 调 infer-service /health；失败返回 null（视为不可用） */
    public PythonHealthResponse probeLocal() {
        try {
            return inferClient.health();
        } catch (Exception e) {
            log.warn("infer-service /health 探测失败: {}", e.getMessage());
            return null;
        }
    }
}
