package com.example.mapchange.common.core.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/** GeoJSON FeatureCollection（多边形明细汇总，设计 §3.5；geometry 计算在 Python，Java 只透传） */
public record FeatureCollection(List<JsonNode> features) {
}
