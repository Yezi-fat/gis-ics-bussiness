package com.example.mapchange.gateway.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 健康聚合（设计 §2.4 路由表：/healthz → gateway 本地聚合各服务 /actuator/health）。
 * M1 骨架：占位返回（实现留待后续，聚合逻辑含推理后端连通性与模型就绪状态，FR-5.5/5.7）。
 */
@RestController
public class HealthAggregateController {

    @GetMapping("/healthz")
    public Mono<Map<String, Object>> healthz() {
        // TODO 后续里程碑：聚合各服务 /actuator/health + 推理后端连通性
        return Mono.just(Map.of("status", "UP", "aggregated", false));
    }
}
