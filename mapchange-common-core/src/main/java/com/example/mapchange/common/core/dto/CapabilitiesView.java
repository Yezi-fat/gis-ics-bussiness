package com.example.mapchange.common.core.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * 系统能力集视图（FR-10.5/10.6/10.7）。
 * partial=true 表示部分下游状态聚合失败降级（对应分组 provider="unknown"，评审 P-06）。
 * inferenceGpuUsable 为 infer-service 上报的 GPU 实际能力（对接事项 J-3，Function-Q1 配套）：
 * 与 inferenceProvider（配置解析口径）区分——provider 可配置强制 local_cpu 而硬件 GPU 可用，反之亦然；
 * 未探测/聚合失败时为 null。
 */
public record CapabilitiesView(Map<String, Boolean> features,
                               String inferenceProvider,
                               Boolean inferenceGpuUsable,
                               String storageProvider,
                               boolean authEnabled,
                               JsonNode tileMatrix,
                               JsonNode defaults,
                               boolean partial) {
}
