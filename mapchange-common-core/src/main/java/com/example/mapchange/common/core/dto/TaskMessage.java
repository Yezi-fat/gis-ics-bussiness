package com.example.mapchange.common.core.dto;

import com.example.mapchange.common.core.enums.TaskType;

import java.util.UUID;

/** 任务队列消息（exchange=mapchange.task，queue=task.inference，设计 §3.1.3） */
public record TaskMessage(UUID taskId, TaskType type) {
}
