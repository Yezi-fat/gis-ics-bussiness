package com.example.mapchange.common.core.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** 配置分组视图（FR-10.4：当前值/默认值/是否被修改 三态） */
public record ConfigGroupView(String group, Map<String, ConfigItem> items) {

    /** 单项配置三态 */
    public record ConfigItem(String value, String defaultValue, boolean modified, JsonNode meta) {
    }
}
