package com.example.mapchange.common.core.dto;

/**
 * 签名 URL 请求（设计 §2.8）。
 * purpose=INTERNAL（供 Python 拉取影像）时短 TTL（默认 10min，可配，评审 P-03）；默认 FRONTEND 2h。
 */
public record SignUrlRequest(String key, String purpose, String ttl) {
}
