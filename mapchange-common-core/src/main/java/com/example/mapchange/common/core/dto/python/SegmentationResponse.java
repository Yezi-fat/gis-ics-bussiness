package com.example.mapchange.common.core.dto.python;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * 语义分割响应（评审 R-02：蒙版 base64 随响应返回，Java 代收上传）。
 * masks：element → base64 PNG；combinedMask：按类别着色的合成蒙版；statistics：各类 area_px/area_m2/ratio/patch_count。
 */
public record SegmentationResponse(String taskId, Map<String, String> masks, String combinedMask,
                                   JsonNode statistics, String modelName, String modelVersion) {
}
