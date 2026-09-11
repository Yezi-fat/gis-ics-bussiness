package com.example.mapchange.common.core.geo;

/** 瓦片范围（CGCS2000 经纬度瓦片方案；x/y 为闭区间） */
public record TileRange(int z, int xMin, int xMax, int yMin, int yMax) {
}
