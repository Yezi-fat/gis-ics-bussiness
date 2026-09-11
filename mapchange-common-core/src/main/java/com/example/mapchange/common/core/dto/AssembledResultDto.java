package com.example.mapchange.common.core.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * 结果组装查询响应（评审 R-01：P-04 重签落点）。
 * 返回图层清单（含实时签名 URL）+ task.result 汇总；全部 storage key 在出口实时批量签名。
 */
public record AssembledResultDto(List<AssembledLayer> layers, JsonNode resultSummary) {

    /** 图层 + 实时签名后的访问 URL */
    public record AssembledLayer(String period, String element, String layerType,
                                 String storageKey, String signedUrl) {
    }
}
