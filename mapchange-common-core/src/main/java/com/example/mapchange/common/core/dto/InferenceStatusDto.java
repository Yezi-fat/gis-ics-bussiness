package com.example.mapchange.common.core.dto;

/** 推理提供方状态（analysis-service /internal/inference/status，供 config-service 聚合 capabilities） */
public record InferenceStatusDto(String provider, String reason, boolean degraded) {
}
