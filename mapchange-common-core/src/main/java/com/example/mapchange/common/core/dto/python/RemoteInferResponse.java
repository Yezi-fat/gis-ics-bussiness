package com.example.mapchange.common.core.dto.python;

import com.fasterxml.jackson.databind.JsonNode;

/** 通用推理服务响应（remote provider；协议字段以联调规范为准，FR-5.4） */
public record RemoteInferResponse(String requestId, JsonNode outputs) {
}
