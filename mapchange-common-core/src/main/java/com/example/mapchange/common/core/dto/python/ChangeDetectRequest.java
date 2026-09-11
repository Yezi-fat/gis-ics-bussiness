package com.example.mapchange.common.core.dto.python;

import com.example.mapchange.common.core.geo.GeoExtent;

/** 端到端变化检测请求（Java↔Python 契约；before/after 为内网预签名 URL） */
public record ChangeDetectRequest(String taskId, String beforeUrl, String afterUrl,
                                  Double threshold, Integer minArea, Boolean autoAlign,
                                  GeoExtent geoExtent, String modelName, String modelVersion) {
}
