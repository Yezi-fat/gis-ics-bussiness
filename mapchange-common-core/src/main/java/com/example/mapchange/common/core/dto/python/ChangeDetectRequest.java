package com.example.mapchange.common.core.dto.python;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 端到端变化检测请求（《Python推理计算服务接口文档》§1.2；before/after 为内网预签名 URL）。
 * auto_align 本期仅透传记录（自动配准列 M6）；超偏差 Python 返 ALIGNMENT_FAILED(422)。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)   // 可选字段为 null 时不下发（Python pydantic 非可空校验，显式 null 会 422）
public record ChangeDetectRequest(String beforeUrl, String afterUrl, double[] geoExtent,
                                  Double threshold, Integer minArea, Boolean autoAlign,
                                  String modelName, String modelVersion) {
}
