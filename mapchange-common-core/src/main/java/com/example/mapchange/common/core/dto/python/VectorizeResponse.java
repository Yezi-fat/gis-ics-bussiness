package com.example.mapchange.common.core.dto.python;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * 轮廓矢量化响应（《Python推理计算服务接口文档》§2.2）。
 * regions：{geojson（Polygon 几何）, area_px, area_m2, centroid, bbox}；
 * bbox 为 Java region 表冗余标量列数据源（需求 14.5 无 PostGIS 方案）。
 */
public record VectorizeResponse(List<RegionItem> regions) {

    public record RegionItem(JsonNode geojson, Double areaPx, Double areaM2,
                             double[] centroid, double[] bbox) {
    }
}
