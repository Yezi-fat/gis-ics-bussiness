package com.example.mapchange.common.core.dto;

import com.example.mapchange.common.core.enums.RegionType;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 区域写入请求（仅 persist.regions=db 时调用，设计 §3.5）。
 * geojson 为多边形 GeoJSON；bbox 冗余标量列供范围检索（无 PostGIS 方案，需求 14.5）。
 */
public record RegionWriteRequest(String period, String element, RegionType regionType,
                                 JsonNode geojson, Double areaM2, Double confidenceAvg,
                                 Double centroidX, Double centroidY,
                                 double bboxMinx, double bboxMiny, double bboxMaxx, double bboxMaxy) {
}
