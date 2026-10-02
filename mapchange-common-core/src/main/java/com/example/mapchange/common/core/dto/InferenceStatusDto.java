package com.example.mapchange.common.core.dto;

/** 推理提供方状态（analysis-service /internal/inference/status，供 config-service 聚合 capabilities）。
 *  gpuUsable 为 infer-service /health 上报的实际 GPU 能力（试建会话通过，对接事项 J-3）；
 *  未探测/探测失败时为 null——与 provider（配置解析口径）区分展示。 */
public record InferenceStatusDto(String provider, String reason, boolean degraded, Boolean gpuUsable) {
}
