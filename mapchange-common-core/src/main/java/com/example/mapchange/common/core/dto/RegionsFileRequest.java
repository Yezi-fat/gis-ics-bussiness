package com.example.mapchange.common.core.dto;

import com.fasterxml.jackson.databind.JsonNode;

/** regions.geojson 写入请求：FeatureCollection 由 Python 计算结果汇总组织（设计 §3.5 第 2 条） */
public record RegionsFileRequest(JsonNode featureCollection) {
}
