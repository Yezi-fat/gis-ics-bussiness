package com.example.mapchange.common.core.dto;

/** GeoJSON 导出响应（FR-4.2；签名 URL 或文件流下载地址） */
public record ExportResponse(String downloadUrl, String expiresAt) {
}
