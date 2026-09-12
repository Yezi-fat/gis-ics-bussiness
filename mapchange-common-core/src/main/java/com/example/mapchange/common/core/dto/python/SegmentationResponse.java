package com.example.mapchange.common.core.dto.python;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * 语义分割响应（《Python推理计算服务接口文档》§1.1）。
 * combined_mask_png_b64：按类别着色的合成蒙版（RGBA）；per_class_masks：各类独立二值蒙版（0/255）；
 * geo_transform：GDAL 六参数仿射，Java 透传给 compute 的 diff/vectorize；
 * actual_provider：实际执行提供方（降级时如实标记，小写连字符，如 local-cpu）。
 */
public record SegmentationResponse(String combinedMaskPngB64, Map<String, String> perClassMasks,
                                   JsonNode statistics, double[] geoTransform, ModelInfo modelInfo,
                                   String actualProvider, Long elapsedMs) {
}
