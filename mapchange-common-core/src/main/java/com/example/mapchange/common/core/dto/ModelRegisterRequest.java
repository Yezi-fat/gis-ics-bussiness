package com.example.mapchange.common.core.dto;

/** 模型登记请求（FR-3.5；modelType: SEG / CHANGE） */
public record ModelRegisterRequest(String modelName, String modelVersion, String modelType,
                                   String storageKey) {
}
