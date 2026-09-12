package com.example.mapchange.common.core.dto.python;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * 自然语言解析响应（FR-9，需求 §7.4；《Python推理计算服务接口文档》§3.1）。
 * intent: feature_extraction / feature_comparison / temporal_analysis；
 * nluProvider: llm / rule-based（降级标记，FR-9.7）；location 可能为 null（FR-9.8 部分成功降级）。
 */
public record NlParseResponse(String intent, JsonNode location, List<String> elements,
                              List<String> periods, Double confidence, boolean needConfirm,
                              List<UnsupportedItem> unsupported, String nluProvider,
                              List<String> uncertainFields) {

    /** 无法映射的要素表述（FR-9.3；reason 含可识别类别列表） */
    public record UnsupportedItem(String raw, String reason) {
    }
}
