package com.example.mapchange.common.core.dto;

/** 要素类别目录项（FR-6.2；modelClassId 为分割模型输出类别 ID 映射） */
public record ElementDto(String id, String name, String color, int modelClassId, boolean enabled) {
}
