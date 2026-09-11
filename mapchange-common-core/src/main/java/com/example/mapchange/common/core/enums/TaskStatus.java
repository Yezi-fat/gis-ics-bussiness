package com.example.mapchange.common.core.enums;

/** 任务状态机（FR-2.1，设计 §3.1.2）：QUEUED → PROCESSING → SUCCESS/FAILED；仅 QUEUED 可取消 */
public enum TaskStatus {
    QUEUED,
    PROCESSING,
    SUCCESS,
    FAILED,
    CANCELLED
}
