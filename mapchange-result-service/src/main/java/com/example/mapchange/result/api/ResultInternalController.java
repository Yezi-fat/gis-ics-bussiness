package com.example.mapchange.result.api;

import com.example.mapchange.common.core.api.ApiResponse;
import com.example.mapchange.common.core.dto.AssembledResultDto;
import com.example.mapchange.common.core.dto.LayerWriteRequest;
import com.example.mapchange.common.core.dto.RegionWriteRequest;
import com.example.mapchange.common.core.dto.RegionsFileRequest;
import com.example.mapchange.common.core.dto.FeatureCollection;
import com.example.mapchange.result.service.ResultAssemblyService;
import com.example.mapchange.result.service.ResultPersistenceService;
import com.example.mapchange.result.domain.LayerRepository;
import com.example.mapchange.result.domain.RegionRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 结果内部接口（供 analysis-service 写结果 / task-service 读结果，设计 §2.7）。
 */
@RestController
@RequestMapping("/internal/results")
public class ResultInternalController {

    private final ResultPersistenceService persistenceService;
    private final ResultAssemblyService assemblyService;
    private final LayerRepository layerRepository;
    private final RegionRepository regionRepository;

    public ResultInternalController(ResultPersistenceService persistenceService,
                                    ResultAssemblyService assemblyService,
                                    LayerRepository layerRepository,
                                    RegionRepository regionRepository) {
        this.persistenceService = persistenceService;
        this.assemblyService = assemblyService;
        this.layerRepository = layerRepository;
        this.regionRepository = regionRepository;
    }

    /**
     * 结果组装查询（P-04 重签落点，评审 R-01）：返回图层清单 + task.result 汇总，
     * 全部 storage key 在本端点出口实时调 storage-service 签名（批量、TTL 走 storage.sign-url-ttl）。
     * M4 调整为 POST + 请求体携带 task.result：result-service 不再回调 task-service 取任务，
     * 避免 task→result→task 循环阻塞（task-service 线程池 Feign 等待耗尽，M4 联调实测）。
     */
    @PostMapping("/{taskId}/assembled")
    public ApiResponse<AssembledResultDto> assembled(@PathVariable UUID taskId,
                                                     @RequestBody(required = false)
                                                     com.fasterxml.jackson.databind.JsonNode resultSummary) {
        return ApiResponse.ok(assemblyService.assemble(taskId, resultSummary));
    }

    /** 图层写入（设计 §3.5 第 1 条） */
    @PostMapping("/{taskId}/layers")
    public ApiResponse<Void> writeLayers(@PathVariable UUID taskId, @RequestBody List<LayerWriteRequest> layers) {
        persistenceService.saveLayers(taskId, layers);
        return ApiResponse.ok(null);
    }

    /** 组织 FeatureCollection → regions.geojson 存存储层，返回 key（设计 §3.5 第 2 条） */
    @PostMapping("/{taskId}/regions-file")
    public ApiResponse<String> writeRegionsFile(@PathVariable UUID taskId, @RequestBody RegionsFileRequest req) {
        List<com.fasterxml.jackson.databind.JsonNode> features = new java.util.ArrayList<>();
        if (req.featureCollection() != null && req.featureCollection().path("features").isArray()) {
            req.featureCollection().path("features").forEach(features::add);
        }
        return ApiResponse.ok(persistenceService.saveRegionsFile(taskId, new FeatureCollection(features)));
    }

    /** region 记录写入（仅 persist.regions=db 时调用） */
    @PostMapping("/{taskId}/regions")
    public ApiResponse<Void> writeRegions(@PathVariable UUID taskId, @RequestBody List<RegionWriteRequest> regions) {
        persistenceService.saveRegions(taskId, regions);
        return ApiResponse.ok(null);
    }

    /** 按任务删除结果记录（清理编排调用，FR-4.3） */
    @DeleteMapping("/{taskId}")
    @Transactional
    public ApiResponse<Void> deleteByTask(@PathVariable UUID taskId) {
        layerRepository.deleteByTaskId(taskId);
        regionRepository.deleteByTaskId(taskId);
        return ApiResponse.ok(null);
    }
}
