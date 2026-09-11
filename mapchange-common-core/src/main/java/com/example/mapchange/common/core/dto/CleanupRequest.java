package com.example.mapchange.common.core.dto;

import java.time.Instant;

/** 清理请求（FR-4.3/4.5；before 之前的完成任务过期清理，或按最旧优先清理至 targetUsageRatio） */
public record CleanupRequest(Instant before, Double targetUsageRatio) {
}
