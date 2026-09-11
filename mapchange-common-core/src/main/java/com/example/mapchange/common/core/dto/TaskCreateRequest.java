package com.example.mapchange.common.core.dto;

import com.example.mapchange.common.core.enums.TaskStatus;
import com.example.mapchange.common.core.enums.TaskType;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * 任务创建请求（task-service 内部接口）。
 * async=true：落库 QUEUED + 投递队列（status/result 忽略）；
 * async=false：已完成任务登记——不投递队列，按 status（SUCCESS/FAILED）直接登记终态并写 result
 * （同步链路事后补录语义，评审 P-07，设计 §3.1.2；status/result/id 字段为 M3 实现期补充）。
 * id：可选——编排层预生成（存储 key 前缀需要，设计 §3.4 key 规范 /{task_id}/...）；
 * 为空时由 task-service 生成。
 */
public record TaskCreateRequest(TaskType taskType, JsonNode payload, String createdBy, boolean async,
                                TaskStatus status, JsonNode result, UUID id) {

    /** 异步任务便捷构造 */
    public static TaskCreateRequest async(UUID id, TaskType taskType, JsonNode payload, String createdBy) {
        return new TaskCreateRequest(taskType, payload, createdBy, true, null, null, id);
    }

    /** 已完成任务登记便捷构造 */
    public static TaskCreateRequest completed(UUID id, TaskType taskType, JsonNode payload, String createdBy,
                                              TaskStatus status, JsonNode result) {
        return new TaskCreateRequest(taskType, payload, createdBy, false, status, result, id);
    }
}
