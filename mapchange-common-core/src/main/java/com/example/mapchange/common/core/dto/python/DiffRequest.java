package com.example.mapchange.common.core.dto.python;

/** 双期差异计算请求（FR-7.2；before/after 为两期同类要素蒙版的预签名 URL） */
public record DiffRequest(String taskId, String element, String beforeMaskUrl, String afterMaskUrl,
                          String addedColor, String removedColor, Integer minArea) {
}
