package com.example.mapchange.common.core.dto.python;

import com.example.mapchange.common.core.geo.GeoExtent;

import java.util.List;
import java.util.Map;

/**
 * 语义分割请求（Java↔Python 契约，细节以 Python 设计文档 §4 与 OpenAPI 快照为准）。
 * 影像引用一律为内网预签名 URL（评审 P-03）；蒙版以 base64 随响应返回（评审 R-02）。
 * elementClassMap：要素类别 ID → 模型输出类别 ID（FR-6.2 映射随请求下发）。
 */
public record SegmentationRequest(String taskId, String imageUrl, List<String> elements,
                                  Map<String, Integer> elementClassMap, Integer minArea,
                                  GeoExtent geoExtent, String modelName, String modelVersion) {
}
