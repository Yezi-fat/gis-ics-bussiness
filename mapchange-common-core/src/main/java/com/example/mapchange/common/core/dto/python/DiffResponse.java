package com.example.mapchange.common.core.dto.python;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 双期差异计算响应（《Python推理计算服务接口文档》§2.1）。
 * diff_mask_png_b64：差异蒙版（RGBA 分色，不变区域透明）；
 * statistics：{added_px, removed_px, net_change_px, change_rate, added_m2, removed_m2}。
 */
public record DiffResponse(String diffMaskPngB64, JsonNode statistics) {
}
