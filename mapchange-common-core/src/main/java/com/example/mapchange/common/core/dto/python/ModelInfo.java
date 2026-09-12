package com.example.mapchange.common.core.dto.python;

import java.util.List;

/** 模型信息（infer-service 响应内嵌，FR-3.3 追溯；class_ids 为模型输出类别表，变化检测模型为 null） */
public record ModelInfo(String name, String version, String provider, List<Integer> classIds) {
}
