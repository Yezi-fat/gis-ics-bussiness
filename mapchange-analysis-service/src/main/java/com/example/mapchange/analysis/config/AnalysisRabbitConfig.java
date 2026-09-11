package com.example.mapchange.analysis.config;

import org.springframework.context.annotation.Configuration;

/**
 * Worker 监听容器配置（J-024）。
 * 重试策略在 AnalysisOrchestrator 编排内实现（瞬时错误 3 次指数退避后置 FAILED）——
 * 不能交给监听容器重试：tryStart 状态守卫只允许 QUEUED→PROCESSING 一次，
 * 容器级重试的第二次调用会被守卫丢弃（消息/状态竞态，M3 联调实测）。
 * 本类保留为队列侧配置的归口（如后续调整 prefetch/并发）。
 */
@Configuration
public class AnalysisRabbitConfig {
    // 暂无定制；监听容器用 Boot 默认（重试/恢复在编排层，DLQ 由队列声明承载）
}
