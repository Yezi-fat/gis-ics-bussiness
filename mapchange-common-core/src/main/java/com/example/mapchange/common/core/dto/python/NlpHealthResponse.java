package com.example.mapchange.common.core.dto.python;

/**
 * nlp-service /health 上报（《Python推理计算服务接口文档》§3.2）。
 * nlu.circuit：进程内熔断器状态（closed/half_open/open，FR-9.7 监控告警）。
 */
public record NlpHealthResponse(String status, NluHealth nlu, GeocoderHealth geocoder) {

    public record NluHealth(String provider, boolean available, String circuit) {
    }

    public record GeocoderHealth(String provider, boolean available) {
    }
}
