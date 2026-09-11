package com.example.mapchange.common.core.dto.python;

import com.fasterxml.jackson.databind.JsonNode;

/** 端到端变化检测响应（mask/probmap 为 base64 PNG；statistics 含数量/面积/中心点） */
public record ChangeMaskResponse(String taskId, String mask, String probmap,
                                 JsonNode statistics, String modelName, String modelVersion) {
}
