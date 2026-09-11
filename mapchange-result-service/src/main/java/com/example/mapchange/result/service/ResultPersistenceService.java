package com.example.mapchange.result.service;

import com.example.mapchange.common.core.dto.FeatureCollection;
import com.example.mapchange.common.core.dto.LayerWriteRequest;
import com.example.mapchange.common.core.dto.RegionWriteRequest;
import com.example.mapchange.common.web.config.RemoteConfigCache;
import com.example.mapchange.result.client.StorageClient;
import com.example.mapchange.result.domain.LayerEntity;
import com.example.mapchange.result.domain.LayerRepository;
import com.example.mapchange.result.domain.RegionEntity;
import com.example.mapchange.result.domain.RegionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * 结果持久化（设计 §3.5，J-027）：layer 表入库、regions.geojson 组织、region 表（可选）写入。
 * persist.regions 三模式：file（默认，GeoJSON 文件）/ db（region 表入库）/ none（跳过）。
 */
@Service
public class ResultPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(ResultPersistenceService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LayerRepository layerRepository;
    private final RegionRepository regionRepository;
    private final StorageClient storageClient;
    private final RemoteConfigCache configCache;

    public ResultPersistenceService(LayerRepository layerRepository, RegionRepository regionRepository,
                                    StorageClient storageClient, RemoteConfigCache configCache) {
        this.layerRepository = layerRepository;
        this.regionRepository = regionRepository;
        this.storageClient = storageClient;
        this.configCache = configCache;
    }

    /** layer 表入库 */
    @Transactional
    public void saveLayers(UUID taskId, List<LayerWriteRequest> layers) {
        List<LayerEntity> entities = layers.stream()
                .map(l -> new LayerEntity(taskId, l.period(), l.element(), l.layerType(), l.storageKey(),
                        l.bbox() != null ? l.bbox().minx() : null, l.bbox() != null ? l.bbox().miny() : null,
                        l.bbox() != null ? l.bbox().maxx() : null, l.bbox() != null ? l.bbox().maxy() : null))
                .toList();
        layerRepository.saveAll(entities);
        log.info("layers saved: taskId={}, count={}", taskId, entities.size());
    }

    /** regions.geojson → storage-service，返回 key（/{task_id}/regions.geojson） */
    public String saveRegionsFile(UUID taskId, FeatureCollection fc) {
        String key = "/%s/regions.geojson".formatted(taskId);
        try {
            ObjectNode fcNode = MAPPER.createObjectNode();
            fcNode.put("type", "FeatureCollection");
            fcNode.set("features", MAPPER.valueToTree(fc.features()));
            storageClient.put(key, MAPPER.writeValueAsBytes(fcNode));
            log.info("regions.geojson saved: taskId={}, key={}", taskId, key);
            return key;
        } catch (Exception e) {
            throw new IllegalStateException("regions.geojson 写入失败: " + e.getMessage(), e);
        }
    }

    /** region 表（JSONB + bbox 冗余列；仅 persist.regions=db 时调用） */
    @Transactional
    public void saveRegions(UUID taskId, List<RegionWriteRequest> regions) {
        if (!"db".equals(configCache.getOrDefault("persist.regions", "file"))) {
            log.info("persist.regions != db，跳过 region 入库: taskId={}", taskId);
            return;
        }
        List<RegionEntity> entities = regions.stream()
                .map(r -> new RegionEntity(taskId, r.period(), r.element(), r.regionType(), r.geojson(),
                        r.areaM2(), r.confidenceAvg(), r.centroidX(), r.centroidY(),
                        r.bboxMinx(), r.bboxMiny(), r.bboxMaxx(), r.bboxMaxy()))
                .toList();
        regionRepository.saveAll(entities);
        log.info("regions saved: taskId={}, count={}", taskId, entities.size());
    }

    /** persist.regions=none 时跳过矢量化明细持久化由调用方控制 */
    public boolean regionsDisabled() {
        return "none".equals(configCache.getOrDefault("persist.regions", "file"));
    }
}
