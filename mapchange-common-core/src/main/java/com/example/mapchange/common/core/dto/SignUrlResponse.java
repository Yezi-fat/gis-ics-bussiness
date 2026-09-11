package com.example.mapchange.common.core.dto;

/** 签名 URL 响应 */
public record SignUrlResponse(String url, long expireAtEpochMs) {
}
