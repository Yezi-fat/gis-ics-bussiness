package com.example.mapchange.common.core.dto;

import com.example.mapchange.common.core.enums.TaskStatus;
import com.fasterxml.jackson.databind.JsonNode;

/** 任务状态流转请求（经状态机，task-service 内部接口） */
public record StatusUpdate(TaskStatus to, String errorCode, String errorMessage, JsonNode result) {
}
