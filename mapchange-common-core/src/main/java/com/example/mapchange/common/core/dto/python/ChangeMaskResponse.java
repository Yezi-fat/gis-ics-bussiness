package com.example.mapchange.common.core.dto.python;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 端到端变化检测响应（《Python推理计算服务接口文档》§1.2）。
 * mask_png_b64：变化蒙版；probmap_png_b64：变化概率图（FR-1.5）；
 * statistics：{change_count, total_area_px, total_area_m2}（FR-1.8）；
 * estimated_shift_px：配准校验实测整体平移量；auto_aligned：本期恒 false（M6）。
 */
public record ChangeMaskResponse(String maskPngB64, String probmapPngB64, JsonNode statistics,
                                 Double estimatedShiftPx, Boolean autoAligned, double[] geoTransform,
                                 ModelInfo modelInfo, String actualProvider, Long elapsedMs) {
}
