package com.example.mapchange.common.core.dto.python;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 轮廓矢量化请求（《Python推理计算服务接口文档》§2.2；二值蒙版 base64 上行）。
 * geo_transform 缺省时 Python 输出像素坐标且 area_m2=null；有值时输出 CGCS2000 经纬度。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)   // 可选字段为 null 时不下发（Python pydantic 非可空校验，显式 null 会 422）
public record VectorizeRequest(String maskB64, Double minArea, double[] geoTransform) {
}
