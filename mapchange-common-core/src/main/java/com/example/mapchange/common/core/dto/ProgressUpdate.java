package com.example.mapchange.common.core.dto;

/** 进度上报（FR-2.4/8.6）；stage 枚举：SEGMENTING / DIFFING / VECTORIZING / UPLOADING */
public record ProgressUpdate(int totalPeriods, int completedPeriods, String stage) {
}
