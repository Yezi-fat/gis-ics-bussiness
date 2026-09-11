package com.example.mapchange.common.core.dto.python;

import com.example.mapchange.common.core.geo.GeoExtent;

/** 矢量化请求（FR-1.7/6.4/7.6；mask 为蒙版预签名 URL 或 base64，以契约测试锁定） */
public record VectorizeRequest(String taskId, String maskUrl, String maskBase64,
                               String period, String element, String regionType,
                               GeoExtent geoExtent) {
}
