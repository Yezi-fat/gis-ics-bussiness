package com.example.mapchange.common.core.util;

import com.example.mapchange.common.core.dto.python.VectorizeResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * compute-service 矢量化 regions → regions.geojson Feature 转换（analysis 即时 / result 懒矢量化共用）。
 * Feature 结构：{type:Feature, geometry, properties:{period, element, region_type, area_px, area_m2, centroid, bbox}}。
 */
public final class RegionFeatureMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RegionFeatureMapper() {
    }

    /** 单张蒙版的 regions → Feature 列表（period/element 可为 null，regionType 见 RegionTypes） */
    public static List<JsonNode> toFeatures(VectorizeResponse resp, String period, String element,
                                            String regionType) {
        List<JsonNode> features = new ArrayList<>();
        if (resp == null || resp.regions() == null) {
            return features;
        }
        for (VectorizeResponse.RegionItem r : resp.regions()) {
            ObjectNode f = MAPPER.createObjectNode();
            f.put("type", "Feature");
            f.set("geometry", r.geojson());
            ObjectNode props = f.putObject("properties");
            if (period != null) {
                props.put("period", period);
            }
            if (element != null) {
                props.put("element", element);
            }
            props.put("region_type", regionType);
            props.put("area_px", r.areaPx());
            if (r.areaM2() != null) {
                props.put("area_m2", r.areaM2());
            }
            ArrayNode centroid = props.putArray("centroid");
            if (r.centroid() != null) {
                for (double v : r.centroid()) {
                    centroid.add(v);
                }
            }
            ArrayNode bbox = props.putArray("bbox");
            if (r.bbox() != null) {
                for (double v : r.bbox()) {
                    bbox.add(v);
                }
            }
            features.add(f);
        }
        return features;
    }

    /** 区域类型（与原 Python 契约 regionType 口径一致：FEATURE/PROBMAP→COVERAGE，DIFF/CHANGE→CHANGED） */
    public static String regionTypeOf(String layerType) {
        return switch (layerType) {
            case "FEATURE", "PROBMAP" -> "COVERAGE";
            default -> "CHANGED";
        };
    }
}
