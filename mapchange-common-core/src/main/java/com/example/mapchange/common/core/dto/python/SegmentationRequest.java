package com.example.mapchange.common.core.dto.python;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * 语义分割请求（Java→Python infer-service 契约，以《Python推理计算服务接口文档》§1.1 为准）。
 * 影像引用一律为内网预签名 URL（评审 P-03）；geo_extent 为 [minx,miny,maxx,maxy] 数组形态；
 * class_mapping/colors 自 element_catalog 下发（J-03/J-06）；task 关联走 X-Task-Id 请求头（MDC 透传）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)   // 可选字段为 null 时不下发（Python pydantic 非可空校验，显式 null 会 422）
public record SegmentationRequest(String imageUrl, double[] geoExtent, List<String> elements,
                                  Map<String, Integer> classMapping, Map<String, String> colors,
                                  Integer minArea, String modelName, String modelVersion) {
}
