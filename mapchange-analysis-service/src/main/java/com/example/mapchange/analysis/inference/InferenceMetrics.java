package com.example.mapchange.analysis.inference;

import com.example.mapchange.common.core.enums.InferenceProvider;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 推理指标（设计 §8.2，J-035）：
 * inference_latency_seconds{provider}（分提供方耗时分布）、inference_degraded_total（降级次数）。
 * 熔断事件由 resilience4j 自动暴露（inference_circuitbreaker_* / resilience4j_circuitbreaker_*）。
 */
@Component
public class InferenceMetrics {

    private final MeterRegistry registry;
    private final Counter degradedTotal;
    private final ConcurrentHashMap<String, Timer> timers = new ConcurrentHashMap<>();

    public InferenceMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.degradedTotal = Counter.builder("inference_degraded_total")
                .description("远程推理降级本地次数").register(registry);
    }

    public Timer timer(InferenceProvider provider) {
        String name = provider != null ? provider.name() : "UNKNOWN";
        return timers.computeIfAbsent(name, p -> Timer.builder("inference_latency_seconds")
                .description("推理耗时分布").tag("provider", p).register(registry));
    }

    public void countDegraded() {
        degradedTotal.increment();
    }
}
