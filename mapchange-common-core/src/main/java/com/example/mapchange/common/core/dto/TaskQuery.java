package com.example.mapchange.common.core.dto;

import com.example.mapchange.common.core.enums.TaskType;

import java.time.Instant;

/** 任务分页查询条件（FR-2.6：按类型过滤、按时间排序分页；按 created_by 过滤在服务端注入，评审 P-09f） */
public record TaskQuery(TaskType taskType, Instant createdAfter, Instant createdBefore,
                        Integer page, Integer size) {
}
