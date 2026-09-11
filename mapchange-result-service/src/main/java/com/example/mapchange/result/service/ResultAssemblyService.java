package com.example.mapchange.result.service;

import com.example.mapchange.common.core.api.BizException;
import com.example.mapchange.common.core.api.ErrorCode;
import com.example.mapchange.common.core.dto.AssembledResultDto;
import com.example.mapchange.common.core.dto.SignUrlRequest;
import com.example.mapchange.common.core.dto.SignUrlResponse;
import com.example.mapchange.result.client.StorageClient;
import com.example.mapchange.result.client.TaskClient;
import com.example.mapchange.result.domain.LayerEntity;
import com.example.mapchange.result.domain.LayerRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 结果组装 + 实时重签（评审 R-01，J-031）：查 layer 记录与 task.result，
 * 对全部 key 批量调 StorageClient.signUrl 现签——
 * 落库只存 storage key、不落签名 URL（评审 P-04），历史结果任意时刻可用。
 * task.result 中所有以 `_key` 结尾的字段（mask_key/combined_mask_key/regions_file_key 等）
 * 在出口补充同名 `_url` 字段（签名后），原 key 字段保留。
 */
@Service
public class ResultAssemblyService {

    private static final Logger log = LoggerFactory.getLogger(ResultAssemblyService.class);

    private final LayerRepository layerRepository;
    private final StorageClient storageClient;

    public ResultAssemblyService(LayerRepository layerRepository, StorageClient storageClient) {
        this.layerRepository = layerRepository;
        this.storageClient = storageClient;
    }

    /** 组装 + 批量签名（TTL 走 storage.sign-url-ttl 配置）。
     *  resultSummary 由调用方（task-service）携带——本服务不回调 task-service（防循环阻塞） */
    public AssembledResultDto assemble(UUID taskId, JsonNode resultSummary) {
        List<LayerEntity> layers = layerRepository.findByTaskId(taskId);

        List<AssembledResultDto.AssembledLayer> assembled = new ArrayList<>();
        for (LayerEntity l : layers) {
            assembled.add(new AssembledResultDto.AssembledLayer(l.getPeriod(), l.getElement(),
                    l.getLayerType().name(), l.getStorageKey(), sign(l.getStorageKey())));
        }
        JsonNode signedSummary = resultSummary != null ? signKeysDeep(resultSummary.deepCopy()) : null;
        return new AssembledResultDto(assembled, signedSummary);
    }

    /** 深度遍历：任何对象中 `*_key` 文本字段补同名 `_url` 字段（实时签名）；数组递归 */
    private JsonNode signKeysDeep(JsonNode node) {
        if (node instanceof ObjectNode o) {
            List<String> keyFields = new ArrayList<>();
            o.fieldNames().forEachRemaining(f -> {
                if (f.endsWith("_key") && o.get(f).isTextual()) {
                    keyFields.add(f);
                }
            });
            keyFields.forEach(f ->
                    o.put(f.substring(0, f.length() - "_key".length()) + "_url", sign(o.get(f).asText())));
            o.forEach(this::signKeysDeep);
        } else if (node instanceof com.fasterxml.jackson.databind.node.ArrayNode a) {
            a.forEach(this::signKeysDeep);
        }
        return node;
    }

    private String sign(String key) {
        try {
            SignUrlResponse resp = storageClient.signUrl(new SignUrlRequest(key, "FRONTEND", null)).data();
            return resp != null ? resp.url() : null;
        } catch (Exception e) {
            log.warn("签名失败（返回 null）: key={}, 原因={}", key, e.getMessage());
            return null;
        }
    }
}
