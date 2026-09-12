package com.example.mapchange.common.core.dto.python;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 双期差异计算请求（《Python推理计算服务接口文档》§2.1；两期同类要素蒙版以 base64 上行）。
 * geo_transform 有值时 Python 输出 added_m2/removed_m2（CGCS2000 椭球逐行积分）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)   // 可选字段为 null 时不下发（Python pydantic 非可空校验，显式 null 会 422）
public record DiffRequest(String beforeMaskB64, String afterMaskB64, String element,
                          Integer minArea, DiffColors colors, double[] geoTransform) {

    /** 差异分色（默认新增绿/减少红；Java 自 capabilities defaults 下发） */
    public record DiffColors(String added, String removed) {
    }
}
