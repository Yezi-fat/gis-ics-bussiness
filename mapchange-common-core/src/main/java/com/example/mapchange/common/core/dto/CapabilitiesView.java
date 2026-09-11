package com.example.mapchange.common.core.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * 系统能力集视图（FR-10.5/10.6/10.7）。
 * partial=true 表示部分下游状态聚合失败降级（对应分组 provider="unknown"，评审 P-06）。
 */
public record CapabilitiesView(Map<String, Boolean> features,
                               String inferenceProvider,
                               String storageProvider,
                               boolean authEnabled,
                               JsonNode tileMatrix,
                               JsonNode defaults,
                               boolean partial) {
}
