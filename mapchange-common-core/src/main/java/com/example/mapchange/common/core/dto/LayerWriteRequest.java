package com.example.mapchange.common.core.dto;

import com.example.mapchange.common.core.enums.LayerType;
import com.example.mapchange.common.core.geo.GeoExtent;

/** 图层写入请求（analysis-service → result-service，设计 §3.5） */
public record LayerWriteRequest(String period, String element, LayerType layerType,
                                String storageKey, GeoExtent bbox) {
}
