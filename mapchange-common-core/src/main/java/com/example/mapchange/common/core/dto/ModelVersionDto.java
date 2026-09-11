package com.example.mapchange.common.core.dto;

import java.time.Instant;

/** 本地模型版本（FR-3.5，model_registry 表对应；status: REGISTERED / ACTIVE / RETIRED） */
public record ModelVersionDto(String modelName, String modelVersion, String modelType,
                              String storageKey, String status, Instant activatedAt,
                              String createdBy, Instant createdAt) {
}
