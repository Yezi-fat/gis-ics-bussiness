package com.example.mapchange.common.core.geo;

import com.example.mapchange.common.core.api.BizException;
import com.example.mapchange.common.core.api.ErrorCode;

import java.util.List;

/**
 * 坐标与瓦片换算工具（CGCS2000 经纬度投影，设计 §3.1.5）。
 * span/expectedExtent 为需求冻结公式的纯函数（评审 T-03 例外，随骨架直接实现并提交单测）；
 * 两个校验方法于 M3（J-021）实现。
 */
public final class GeoExtentUtils {

    /** 一致性校验容差 */
    public static final double TOLERANCE = 1e-9;

    private GeoExtentUtils() {
    }

    /** span(z) = spanBase / 2^z（度）；瓦片矩阵参数由配置下发，禁止写死 */
    public static double span(int z, TileMatrixParam param) {
        return param.spanBase() / Math.pow(2, z);
    }

    /**
     * tile_range → 期望 geo_extent（线性公式，设计 §3.1.5）：
     * [origin.x + x_min*span, origin.y - (y_max+1)*span, origin.x + (x_max+1)*span, origin.y - y_min*span]
     */
    public static GeoExtent expectedExtent(TileRange range, TileMatrixParam param) {
        double span = span(range.z(), param);
        double originX = param.origin()[0];
        double originY = param.origin()[1];
        return new GeoExtent(
                originX + range.xMin() * span,
                originY - (range.yMax() + 1) * span,
                originX + (range.xMax() + 1) * span,
                originY - range.yMin() * span);
    }

    /** 一致性校验（J-021）：容差 {@link #TOLERANCE}，不一致抛 INVALID_INPUT */
    public static void validateConsistency(TileRange range, GeoExtent extent, TileMatrixParam param) {
        GeoExtent expected = expectedExtent(range, param);
        if (!approxEquals(expected, extent)) {
            throw new BizException(ErrorCode.INVALID_INPUT,
                    "tile_range 与 geo_extent 不一致：expected=" + expected + ", actual=" + extent);
        }
    }

    /** 多期任务各面板 geo_extent 逐项相等校验（J-021），不一致抛 GEO_EXTENT_MISMATCH（FR-8.7） */
    public static void validateSameExtent(List<GeoExtent> extents) {
        if (extents == null || extents.size() < 2) {
            return;
        }
        GeoExtent first = extents.get(0);
        for (int i = 1; i < extents.size(); i++) {
            if (!approxEquals(first, extents.get(i))) {
                throw new BizException(ErrorCode.GEO_EXTENT_MISMATCH,
                        "多期影像地理范围不一致：第 1 期 " + first + " vs 第 " + (i + 1) + " 期 " + extents.get(i));
            }
        }
    }

    private static boolean approxEquals(GeoExtent a, GeoExtent b) {
        return Math.abs(a.minx() - b.minx()) <= TOLERANCE
                && Math.abs(a.miny() - b.miny()) <= TOLERANCE
                && Math.abs(a.maxx() - b.maxx()) <= TOLERANCE
                && Math.abs(a.maxy() - b.maxy()) <= TOLERANCE;
    }
}
