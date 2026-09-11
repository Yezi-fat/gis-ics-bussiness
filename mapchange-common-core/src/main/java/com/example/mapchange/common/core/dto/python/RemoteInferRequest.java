package com.example.mapchange.common.core.dto.python;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 通用推理服务请求（remote provider；完整端点 URL 直调，需求约束 #10）。
 * 协议字段按与推理服务方的联调规范冻结（FR-5.4，J-037 产出联调文档）。
 */
public record RemoteInferRequest(String modelName, String modelVersion, String taskType,
                                 JsonNode inputs, JsonNode params) {
}
