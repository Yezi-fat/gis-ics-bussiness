package com.example.mapchange.analysis.inference;

import com.example.mapchange.common.core.dto.InferenceStatusDto;
import com.example.mapchange.common.core.dto.python.InferHealthResponse;
import com.example.mapchange.common.core.enums.InferenceProvider;
import com.example.mapchange.common.web.config.ConfigGroupRefreshedEvent;
import com.example.mapchange.common.web.config.RemoteConfigCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 推理提供方三级解析（FR-5.6/5.7，设计 §3.2）：启动时执行一次——
 * ① 配置了 remote.endpoint 且连通 → REMOTE；② 探测本地 GPU → LOCAL_GPU；③ 否则 LOCAL_CPU。
 * 未配置 remote 属正常部署形态，记 INFO 不记 ERROR/WARN。
 * config-service 未就绪时按 L1 yml 初值解析 + WARN + 后台重试回源（评审 T-04）；
 * inference 组配置变更广播后热重解析（reResolve）。
 */
@Component
public class InferenceProviderResolver {

    private static final Logger log = LoggerFactory.getLogger(InferenceProviderResolver.class);

    private final RemoteConfigCache configCache;
    private final EnvironmentProbe probe;

    private final AtomicReference<InferenceProvider> current = new AtomicReference<>();
    private final AtomicReference<String> reason = new AtomicReference<>("未解析");
    /** 最近一次 infer-service /health 结果（J-04/B-4 类别一致性校验、J-08 聚合用） */
    private final AtomicReference<InferHealthResponse> lastInferHealth = new AtomicReference<>();

    public InferenceProviderResolver(RemoteConfigCache configCache, EnvironmentProbe probe) {
        this.configCache = configCache;
        this.probe = probe;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resolveOnStartup() {
        reResolve();
    }

    /** inference 组配置变更广播后热重解析（FR-3.4 切换无需重启） */
    @EventListener(ConfigGroupRefreshedEvent.class)
    public void onConfigRefreshed(ConfigGroupRefreshedEvent event) {
        if ("inference".equals(event.getGroup())) {
            log.info("inference 组配置变更，重新解析推理提供方");
            reResolve();
        }
    }

    public InferenceProvider current() {
        return current.get();
    }

    /** 最近一次 infer /health（可能为 null：未探测/探测失败） */
    public InferHealthResponse lastInferHealth() {
        return lastInferHealth.get();
    }

    public InferenceStatusDto status() {
        InferHealthResponse health = lastInferHealth.get();
        return new InferenceStatusDto(
                current.get() != null ? current.get().name() : "UNKNOWN", reason.get(), false,
                health != null ? health.gpuUsable() : null);
    }

    /** 执行三级解析；任何失败不阻断启动 */
    public void reResolve() {
        try {
            String providerCfg = configCache.getOrDefault("inference.provider", "auto");
            String endpoint = configCache.getOrDefault("inference.remote.endpoint", "");
            InferenceProvider resolved = switch (providerCfg) {
                case "remote" -> {
                    if (!probe.checkRemote(endpoint)) {
                        log.warn("强制指定 remote 但端点不可达（FR-5.7 冲突告警）: {}", endpoint);
                    }
                    yield InferenceProvider.REMOTE;
                }
                case "local-gpu" -> InferenceProvider.LOCAL_GPU;
                case "local-cpu" -> InferenceProvider.LOCAL_CPU;
                default -> autoResolve(endpoint);
            };
            current.set(resolved);
            reason.set("provider 配置=%s，解析结果=%s".formatted(providerCfg, resolved));
            log.info("推理提供方解析完成: provider={}, 配置={}, endpoint={}", resolved, providerCfg,
                    endpoint == null || endpoint.isBlank() ? "(未配置)" : endpoint);
            // B-3：顺带探测 nlp-service 健康（熔断器状态监控告警，FR-9.7）；失败不影响解析
            probe.probeNlp();
        } catch (Exception e) {
            // config-service 未就绪等异常：按 L1 兜底 LOCAL_CPU，不阻断启动（评审 T-04）
            current.set(InferenceProvider.LOCAL_CPU);
            reason.set("解析异常，按本地 CPU 兜底: " + e.getMessage());
            log.warn("推理提供方解析异常，按 LOCAL_CPU 兜底（后台可重试）: {}", e.getMessage());
        }
    }

    private InferenceProvider autoResolve(String endpoint) {
        // ① 远程
        if (endpoint != null && !endpoint.isBlank() && probe.checkRemote(endpoint)) {
            return InferenceProvider.REMOTE;
        }
        // ② 本地 GPU（infer-service 上报；以 gpu_usable（试建会话通过）为准，B-3）
        InferHealthResponse health = probe.probeLocal();
        lastInferHealth.set(health);
        if (health != null && health.gpuUsable()) {
            return InferenceProvider.LOCAL_GPU;
        }
        // ③ 本地 CPU
        return InferenceProvider.LOCAL_CPU;
    }
}
