package com.example.mapchange.common.core.dto.python;

import com.fasterxml.jackson.databind.JsonNode;

/** 双期差异计算响应（diffMask 为 base64 分色蒙版；statistics 含 added/removed/net/change_rate 双单位） */
public record DiffResponse(String taskId, String element, String diffMask, JsonNode statistics) {
}
