package com.example.mapchange.common.core.dto;

/** 清理报告（FR-4.3：分批进行、失败可重试） */
public record CleanupReport(int tasksDeleted, int objectsDeleted, int failures) {
}
