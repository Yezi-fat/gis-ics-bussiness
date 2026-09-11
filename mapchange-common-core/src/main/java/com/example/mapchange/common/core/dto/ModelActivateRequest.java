package com.example.mapchange.common.core.dto;

/** 模型激活请求（FR-3.5；激活按 model_type 写对应 L2 键对，设计 §3.7） */
public record ModelActivateRequest(String modelName, String modelVersion) {
}
