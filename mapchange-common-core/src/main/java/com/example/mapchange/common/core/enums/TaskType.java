package com.example.mapchange.common.core.enums;

/** 任务类型（FR-2.2 统一任务模型，设计 §3.1.1） */
public enum TaskType {
    /** 单期要素识别 */
    FEATURE_EXTRACTION,
    /** 双期要素差异比对 */
    FEATURE_COMPARISON,
    /** 多期时序分析 */
    TEMPORAL_ANALYSIS,
    /** 端到端变化检测 */
    CHANGE_DETECTION
}
