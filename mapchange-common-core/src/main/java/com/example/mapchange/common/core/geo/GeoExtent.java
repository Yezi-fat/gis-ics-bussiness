package com.example.mapchange.common.core.geo;

/** 地理范围（CGCS2000 经纬度，[minx, miny, maxx, maxy]） */
public record GeoExtent(double minx, double miny, double maxx, double maxy) {
}
