package com.example.mapchange.common.core.enums;

/** 图层类型（设计 §4 layer 表；差异为单张分色图，新增/减少按图例着色，评审 P-09d） */
public enum LayerType {
    /** 要素覆盖蒙版 */
    FEATURE,
    /** 差异分色蒙版（新增/减少单图分色） */
    DIFF,
    /** 端到端变化蒙版 */
    CHANGE,
    /** 变化概率图 */
    PROBMAP
}
