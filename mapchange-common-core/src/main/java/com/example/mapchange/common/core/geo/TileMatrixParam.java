package com.example.mapchange.common.core.geo;

/**
 * 瓦片矩阵参数（FR-10.6）：由 config-service 统一下发，前后端同源，禁止代码写死。
 * 默认值仅为兜底：origin=[-180,90]，spanBase=360，startLevel=1。
 */
public record TileMatrixParam(double[] origin, double spanBase, int startLevel) {

    public static TileMatrixParam defaults() {
        return new TileMatrixParam(new double[]{-180.0, 90.0}, 360.0, 1);
    }
}
