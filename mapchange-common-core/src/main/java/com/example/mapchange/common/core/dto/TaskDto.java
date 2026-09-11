package com.example.mapchange.common.core.dto;

import com.example.mapchange.common.core.enums.TaskStatus;
import com.example.mapchange.common.core.enums.TaskType;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * 任务传输契约（task-service 对外/对内统一）。
 * queuePosition/estimatedWaitMs：FR-2.5 排队位置/预计等待（仅 QUEUED 态返回，其余为 null，评审 P-09a）。
 * result 中一律只存 storage key、不落签名 URL（评审 P-04）。
 */
public record TaskDto(UUID id, TaskType taskType, TaskStatus status, JsonNode progress,
                      Integer queuePosition, Long estimatedWaitMs,
                      JsonNode payload, String provider, String modelName, String modelVersion,
                      boolean degraded, JsonNode result, String errorCode, String errorMessage,
                      String createdBy, Instant createdAt, Instant finishedAt) {
}
