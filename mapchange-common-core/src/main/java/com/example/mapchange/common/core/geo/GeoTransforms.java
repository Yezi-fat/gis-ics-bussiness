package com.example.mapchange.common.core.geo;

/**
 * GDAL 六参数仿射（geo_transform）工具。
 * 优先使用 infer-service 响应返回的 geo_transform（Python 权威值，GeoTIFF 场景含内嵌 CRS 推导）；
 * 缺省时（remote provider / 懒矢量化只有蒙版与 geo_extent）按 PNG/JPEG 线性公式由
 * geo_extent + 影像像素尺寸推导（与 Python 侧口径一致，经度正、纬度负）。
 */
public final class GeoTransforms {

    private GeoTransforms() {
    }

    /** 由地理范围与像素尺寸推导六参数仿射：[x0, px_w, 0, y0, 0, -px_h]（度/像素） */
    public static double[] fromExtent(GeoExtent extent, int widthPx, int heightPx) {
        double pxW = (extent.maxx() - extent.minx()) / widthPx;
        double pxH = (extent.maxy() - extent.miny()) / heightPx;
        return new double[]{extent.minx(), pxW, 0.0, extent.maxy(), 0.0, -pxH};
    }
}
