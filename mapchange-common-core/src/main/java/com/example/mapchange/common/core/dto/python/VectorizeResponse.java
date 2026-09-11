package com.example.mapchange.common.core.dto.python;

import com.fasterxml.jackson.databind.JsonNode;

/** 矢量化响应：features 为 GeoJSON Feature 数组（含面积/中心点/置信度属性） */
public record VectorizeResponse(String taskId, JsonNode features) {
}
