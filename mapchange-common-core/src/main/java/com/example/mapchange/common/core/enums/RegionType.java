package com.example.mapchange.common.core.enums;

/** 区域类型（设计 §4 region 表；region 表默认不启用，persist.regions=db 时使用） */
public enum RegionType {
    COVERAGE,
    ADDED,
    REMOVED,
    CHANGED
}
