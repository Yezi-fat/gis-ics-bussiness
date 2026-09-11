package com.example.mapchange.common.core.dto;

/** 存储用量（FR-4.5 空间保护） */
public record StorageUsage(long usedBytes, long totalBytes, double usageRatio) {
}
