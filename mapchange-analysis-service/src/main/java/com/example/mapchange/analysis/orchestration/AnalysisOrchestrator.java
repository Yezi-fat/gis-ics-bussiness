package com.example.mapchange.analysis.orchestration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import com.example.mapchange.analysis.client.PythonComputeClient;
import com.example.mapchange.analysis.client.ResultClient;
import com.example.mapchange.analysis.client.StorageClient;
import com.example.mapchange.analysis.client.TaskClient;
import com.example.mapchange.analysis.inference.InferenceProviderResolver;
import com.example.mapchange.analysis.inference.InferenceRouter;
import com.example.mapchange.analysis.validation.AnalysisRequestValidator;
import com.example.mapchange.common.core.api.BizException;
import com.example.mapchange.common.core.api.ErrorCode;
import com.example.mapchange.common.core.api.ImageUrlExpiredException;
import com.example.mapchange.common.core.dto.ElementDto;
import com.example.mapchange.common.core.dto.LayerWriteRequest;
import com.example.mapchange.common.core.dto.ProgressUpdate;
import com.example.mapchange.common.core.dto.RegionsFileRequest;
import com.example.mapchange.common.core.dto.SignUrlRequest;
import com.example.mapchange.common.core.dto.StatusUpdate;
import com.example.mapchange.common.core.dto.TaskCreateRequest;
import com.example.mapchange.common.core.dto.python.ChangeDetectRequest;
import com.example.mapchange.common.core.dto.python.ChangeMaskResponse;
import com.example.mapchange.common.core.dto.python.DiffRequest;
import com.example.mapchange.common.core.dto.python.SegmentationRequest;
import com.example.mapchange.common.core.dto.python.SegmentationResponse;
import com.example.mapchange.common.core.dto.python.VectorizeRequest;
import com.example.mapchange.common.core.enums.LayerType;
import com.example.mapchange.common.core.enums.TaskStatus;
import com.example.mapchange.common.core.enums.TaskType;
import com.example.mapchange.common.core.geo.GeoExtent;
import com.example.mapchange.common.core.geo.GeoTransforms;
import com.example.mapchange.common.core.util.PngSizeReader;
import com.example.mapchange.common.core.util.RegionFeatureMapper;
import com.example.mapchange.common.web.config.RemoteConfigCache;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 分析编排核心（设计 §2.6/§5）。
 * M3 实现 FEATURE_EXTRACTION；M4 补齐 FEATURE_COMPARISON（J-028）/ TEMPORAL_ANALYSIS（J-029）/
 * CHANGE_DETECTION（J-030）；联调期对齐《Python推理计算服务接口文档》真实契约——
 * 影像先经 storage-service 入库、以预签名 URL 传 infer-service（评审 P-03，执行期签发 J-02/B-1）；
 * 蒙版由 Python 响应体 base64 返回、Java 代收上传（评审 R-02）；compute 输入蒙版同格式上行；
 * class_mapping/colors 自 element_catalog 下发（J-03/J-06/C-2）；geo_transform 取 infer 响应透传
 * compute（缺省时按 geo_extent + 蒙版像素尺寸推导）；
 * 落库只存 storage key，签名 URL 在响应/查询出口实时签发（评审 P-04）。
 * 重试在编排内（瞬时错误 3 次指数退避），容器不重试（状态守卫竞态，M3 联调结论）。
 */
@Service
public class AnalysisOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AnalysisOrchestrator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TaskClient taskClient;
    private final StorageClient storageClient;
    private final ResultClient resultClient;
    private final PythonComputeClient computeClient;
    private final InferenceRouter inferenceRouter;
    private final InferenceProviderResolver resolver;
    private final AnalysisRequestValidator validator;
    private final TaskProgressReporter progressReporter;
    private final RemoteConfigCache configCache;

    public AnalysisOrchestrator(TaskClient taskClient, StorageClient storageClient,
                                ResultClient resultClient, PythonComputeClient computeClient,
                                InferenceRouter inferenceRouter, InferenceProviderResolver resolver,
                                AnalysisRequestValidator validator,
                                TaskProgressReporter progressReporter, RemoteConfigCache configCache) {
        this.taskClient = taskClient;
        this.storageClient = storageClient;
        this.resultClient = resultClient;
        this.computeClient = computeClient;
        this.inferenceRouter = inferenceRouter;
        this.resolver = resolver;
        this.validator = validator;
        this.progressReporter = progressReporter;
        this.configCache = configCache;
    }

    // ================= 同步链路（FR-2.3，J-023） =================

    /** 同步执行要素识别（影像由 Controller 上传后传入其 storage key） */
    public ObjectNode runSyncFeatureExtraction(UUID taskId, String imageKey, List<ElementDto> elements,
                                               Integer minArea, GeoExtent geoExtent, String createdBy)
            throws IOException {
        long start = System.currentTimeMillis();
        ObjectNode payload = basePayload(imageKey, elements, minArea, geoExtent);
        try {
            SegOutcome outcome = segmentImage(taskId, imageKey, null, elements, minArea, geoExtent, true);
            String regionsFileKey = vectorizeIfEager(taskId, outcome.allMasks(), minArea);
            ObjectNode result = buildExtractionResult(taskId, outcome, regionsFileKey, geoExtent,
                    System.currentTimeMillis() - start, true);
            taskClient.create(TaskCreateRequest.completed(taskId, TaskType.FEATURE_EXTRACTION,
                    payload, createdBy, TaskStatus.SUCCESS, maskUrlsAsKeys(result)));
            return result;
        } catch (BizException e) {
            registerSyncFailure(taskId, payload, createdBy);
            throw e;
        } catch (Exception e) {
            registerSyncFailure(taskId, payload, createdBy);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "同步识别失败: " + e.getMessage());
        }
    }

    private void registerSyncFailure(UUID taskId, JsonNode payload, String createdBy) {
        try {
            taskClient.create(TaskCreateRequest.completed(taskId, TaskType.FEATURE_EXTRACTION,
                    payload, createdBy, TaskStatus.FAILED, null));
        } catch (Exception e) {
            log.warn("同步任务失败登记失败: {}", e.getMessage());
        }
    }

    // ================= 异步链路（FR-2.4） =================

    /** 建任务 + 入队，返回 task_id（大图自动分流，需求 §7.1） */
    public UUID submitAsync(UUID taskId, TaskType type, JsonNode payload, String createdBy) {
        var resp = taskClient.create(TaskCreateRequest.async(taskId, type, payload, createdBy));
        if (resp.data() == null) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "任务创建失败");
        }
        log.info("async task submitted: id={}, type={}", taskId, type);
        return taskId;
    }

    /** Worker 执行体（J-024）：分类型编排；瞬时错误编排内重试 3 次（指数退避），确定性错误立即置 FAILED */
    public void execute(UUID taskId, TaskType type) {
        int maxAttempts = 3;
        long backoffMs = 1000;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                switch (type) {
                    case FEATURE_EXTRACTION -> doExecuteFeatureExtraction(taskId);
                    case FEATURE_COMPARISON -> doExecuteComparison(taskId);
                    case TEMPORAL_ANALYSIS -> doExecuteTemporal(taskId);
                    case CHANGE_DETECTION -> doExecuteChangeDetection(taskId);
                }
                log.info("async task done: id={}, type={}, attempt={}", taskId, type, attempt);
                return;
            } catch (BizException e) {
                if (!isTransient(e.errorCode()) || attempt == maxAttempts) {
                    markFailed(taskId, e.errorCode().name(), e.getMessage());
                    return;
                }
                log.warn("任务执行失败（瞬时，将重试 {}/{}）: id={}, code={}", attempt, maxAttempts,
                        taskId, e.errorCode());
                sleepQuietly(backoffMs);
                backoffMs *= 2;
            } catch (Exception e) {
                if (attempt == maxAttempts) {
                    markFailed(taskId, ErrorCode.INTERNAL_ERROR.name(), e.getMessage());
                    return;
                }
                log.warn("任务执行异常（将重试 {}/{}）: id={}, {}", attempt, maxAttempts, taskId, e.getMessage());
                sleepQuietly(backoffMs);
                backoffMs *= 2;
            }
        }
    }

    // ================= J-024 单期要素识别（异步） =================

    private void doExecuteFeatureExtraction(UUID taskId) throws Exception {
        JsonNode p = payloadOf(taskId);
        GeoExtent extent = readGeoExtent(p.path("geo_extent"));
        List<ElementDto> elements = readElements(p);
        Integer minArea = p.hasNonNull("min_area") ? p.get("min_area").asInt() : null;

        progressReporter.report(taskId, new ProgressUpdate(1, 0, "SEGMENTING"));
        SegOutcome outcome = segmentImage(taskId, p.path("image_ref").asText(), null, elements,
                minArea, extent, false);
        progressReporter.report(taskId, new ProgressUpdate(1, 0, "VECTORIZING"));
        String regionsFileKey = vectorizeIfEager(taskId, outcome.allMasks(), minArea);
        progressReporter.report(taskId, new ProgressUpdate(1, 1, "UPLOADING"));
        ObjectNode result = buildExtractionResult(taskId, outcome, regionsFileKey, extent, 0, false);
        taskClient.updateStatus(taskId, new StatusUpdate(TaskStatus.SUCCESS, null, null,
                maskUrlsAsKeys(result)));
    }

    // ================= J-028 双期要素差异比对（FR-7） =================

    private void doExecuteComparison(UUID taskId) throws Exception {
        JsonNode p = payloadOf(taskId);
        GeoExtent extent = readGeoExtent(p.path("geo_extent"));
        List<ElementDto> elements = readElements(p);
        Integer minArea = p.hasNonNull("min_area") ? p.get("min_area").asInt() : null;

        progressReporter.report(taskId, new ProgressUpdate(2, 0, "SEGMENTING"));
        SegOutcome before = segmentImage(taskId, p.path("before_ref").asText(), "before",
                elements, minArea, extent, false);
        progressReporter.report(taskId, new ProgressUpdate(2, 1, "SEGMENTING"));
        SegOutcome after = segmentImage(taskId, p.path("after_ref").asText(), "after",
                elements, minArea, extent, false);

        // 差异计算（FR-7.2，compute-service；蒙版 base64 上行 + geo_transform 透传）+ 差异图层（FR-7.3 分色）
        progressReporter.report(taskId, new ProgressUpdate(2, 1, "DIFFING"));
        List<MaskRef> allMasks = new ArrayList<>(before.allMasks());
        allMasks.addAll(after.allMasks());
        ArrayNode masksArr = MAPPER.createArrayNode();
        ObjectNode stats = MAPPER.createObjectNode();
        ObjectNode legend = MAPPER.createObjectNode();
        DiffRequest.DiffColors colors = diffColors();
        legend.put("added", colors.added());
        legend.put("removed", colors.removed());
        for (ElementDto el : elements) {
            String beforeB64 = before.masksB64().get(el.id());
            String afterB64 = after.masksB64().get(el.id());
            if (beforeB64 == null || afterB64 == null) {
                continue;
            }
            var diffResp = computeClient.diff(new DiffRequest(beforeB64, afterB64, el.id(),
                    minArea, colors, before.geoTransform()));
            String diffKey = "/%s/diff_before_after_%s.png".formatted(taskId, el.id());
            uploadBase64(diffResp.diffMaskPngB64(), diffKey);
            resultClient.writeLayers(taskId, List.of(
                    new LayerWriteRequest(null, el.id(), LayerType.DIFF, diffKey, extent)));
            allMasks.add(new MaskRef(null, el.id(), LayerType.DIFF, diffKey,
                    diffResp.diffMaskPngB64(), before.geoTransform()));

            ObjectNode m = masksArr.addObject();
            m.put("element", el.id());
            m.put("before_mask_key", before.maskKeys().get(el.id()));
            m.put("after_mask_key", after.maskKeys().get(el.id()));
            m.put("diff_mask_key", diffKey);
            if (diffResp.statistics() != null) {
                stats.set(el.id(), diffResp.statistics());
            }
        }

        progressReporter.report(taskId, new ProgressUpdate(2, 2, "VECTORIZING"));
        String regionsFileKey = vectorizeIfEager(taskId, allMasks, minArea);

        ObjectNode result = MAPPER.createObjectNode();
        result.put("task_id", taskId.toString());
        result.putPOJO("elements", elements.stream().map(ElementDto::id).toList());
        result.set("geo_extent", extentNode(extent));
        result.set("masks", masksArr);
        result.set("diff_legend", legend);
        result.set("statistics", stats);
        if (regionsFileKey != null) {
            result.put("regions_file_key", regionsFileKey);
        }
        result.set("inference", inferenceNode(before.resp(), before.degraded() || after.degraded()));
        taskClient.updateStatus(taskId, new StatusUpdate(TaskStatus.SUCCESS, null, null, result));
    }

    // ================= J-029 多期时序分析（FR-8） =================

    private void doExecuteTemporal(UUID taskId) throws Exception {
        JsonNode p = payloadOf(taskId);
        GeoExtent extent = readGeoExtent(p.path("geo_extent"));
        List<ElementDto> elements = readElements(p);
        Integer minArea = p.hasNonNull("min_area") ? p.get("min_area").asInt() : null;
        List<String> periods = new ArrayList<>();
        List<String> imageRefs = new ArrayList<>();
        p.path("images").forEach(n -> {
            periods.add(n.path("period").asText());
            imageRefs.add(n.path("ref").asText());
        });
        int n = periods.size();

        // 逐期分割（FR-8.2），任一期失败整体 FAILED 并指明期次（FR-8.6）
        List<SegOutcome> outcomes = new ArrayList<>();
        List<MaskRef> allMasks = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            progressReporter.report(taskId, new ProgressUpdate(n, i, "SEGMENTING"));
            try {
                SegOutcome outcome = segmentImage(taskId, imageRefs.get(i), periods.get(i),
                        elements, minArea, extent, false);
                outcomes.add(outcome);
                allMasks.addAll(outcome.allMasks());
            } catch (Exception e) {
                throw new BizException(ErrorCode.INTERNAL_ERROR,
                        "第 %s 期（%s）识别失败: %s".formatted(i + 1, periods.get(i), e.getMessage()));
            }
        }

        // 相邻期差异（FR-8.3，共 N-1 组；蒙版 base64 上行 + 前一期 geo_transform 透传）
        progressReporter.report(taskId, new ProgressUpdate(n, n, "DIFFING"));
        ArrayNode diffs = MAPPER.createArrayNode();
        DiffRequest.DiffColors colors = diffColors();
        for (int i = 0; i < n - 1; i++) {
            for (ElementDto el : elements) {
                String beforeB64 = outcomes.get(i).masksB64().get(el.id());
                String afterB64 = outcomes.get(i + 1).masksB64().get(el.id());
                if (beforeB64 == null || afterB64 == null) {
                    continue;
                }
                var diffResp = computeClient.diff(new DiffRequest(beforeB64, afterB64, el.id(),
                        minArea, colors, outcomes.get(i).geoTransform()));
                String diffKey = "/%s/diff_%s_%s_%s.png".formatted(taskId, periods.get(i),
                        periods.get(i + 1), el.id());
                uploadBase64(diffResp.diffMaskPngB64(), diffKey);
                resultClient.writeLayers(taskId, List.of(new LayerWriteRequest(
                        periods.get(i) + "_" + periods.get(i + 1), el.id(), LayerType.DIFF,
                        diffKey, extent)));
                allMasks.add(new MaskRef(periods.get(i) + "_" + periods.get(i + 1), el.id(),
                        LayerType.DIFF, diffKey, diffResp.diffMaskPngB64(),
                        outcomes.get(i).geoTransform()));
                diffs.addObject().put("from", periods.get(i)).put("to", periods.get(i + 1))
                        .put("element", el.id()).put("diff_mask_key", diffKey);
            }
        }

        // 面积序列与相邻期变化（FR-8.5）
        ObjectNode areaSeries = MAPPER.createObjectNode();
        ArrayNode periodStats = MAPPER.createArrayNode();
        ArrayNode masksArr = MAPPER.createArrayNode();
        for (int i = 0; i < n; i++) {
            String period = periods.get(i);
            ObjectNode periodStat = periodStats.addObject();
            periodStat.put("period", period);
            for (ElementDto el : elements) {
                JsonNode stat = statisticsOf(outcomes.get(i).resp().statistics(), el.id());
                if (stat == null) {
                    continue;
                }
                double areaM2 = stat.path("area_m2").asDouble(0);
                ObjectNode maskNode = masksArr.addObject();
                maskNode.put("period", period);
                maskNode.put("element", el.id());
                maskNode.put("mask_key", outcomes.get(i).maskKeys().get(el.id()));
                maskNode.put("area_px", stat.path("area_px").asLong(0));
                maskNode.put("area_m2", areaM2);
                // area_series
                ArrayNode series = areaSeries.withArray(el.id());
                series.add(areaM2);
                // period_stats
                ObjectNode ps = periodStat.putObject(el.id());
                ps.put("area_m2", areaM2);
                if (i > 0) {
                    JsonNode prevStat = statisticsOf(outcomes.get(i - 1).resp().statistics(), el.id());
                    double prevArea = prevStat != null ? prevStat.path("area_m2").asDouble(0) : 0;
                    ps.put("change_m2", areaM2 - prevArea);
                    ps.put("change_rate", prevArea == 0 ? null : (areaM2 - prevArea) / prevArea);
                }
            }
        }

        progressReporter.report(taskId, new ProgressUpdate(n, n, "VECTORIZING"));
        String regionsFileKey = vectorizeIfEager(taskId, allMasks, minArea);

        ObjectNode result = MAPPER.createObjectNode();
        result.put("task_id", taskId.toString());
        result.putPOJO("periods", periods);
        result.set("geo_extent", extentNode(extent));
        result.set("masks", masksArr);
        result.set("diffs", diffs);
        result.set("area_series", areaSeries);
        result.set("period_stats", periodStats);
        if (regionsFileKey != null) {
            result.put("regions_file_key", regionsFileKey);
        }
        boolean degraded = outcomes.stream().anyMatch(SegOutcome::degraded);
        result.set("inference", inferenceNode(outcomes.get(0).resp(), degraded));
        progressReporter.report(taskId, new ProgressUpdate(n, n, "UPLOADING"));
        taskClient.updateStatus(taskId, new StatusUpdate(TaskStatus.SUCCESS, null, null, result));
    }

    // ================= J-030 端到端变化检测（FR-1） =================

    private void doExecuteChangeDetection(UUID taskId) throws Exception {
        JsonNode p = payloadOf(taskId);
        GeoExtent extent = readGeoExtent(p.path("geo_extent"));
        Double threshold = p.hasNonNull("threshold") ? p.get("threshold").asDouble() : null;
        Integer minArea = p.hasNonNull("min_area") ? p.get("min_area").asInt() : null;
        Boolean autoAlign = p.hasNonNull("auto_align") && p.get("auto_align").asBoolean();

        progressReporter.report(taskId, new ProgressUpdate(1, 0, "SEGMENTING"));
        String changeModelName = configCache.getOrDefault("inference.local-change-model-name", "");
        String changeModelVersion = configCache.getOrDefault("inference.local-change-model-version", "");
        String beforeKey = p.path("before_ref").asText();
        String afterKey = p.path("after_ref").asText();
        var routed = detectChangeWithResignRetry(taskId, beforeKey, afterKey, threshold, minArea,
                autoAlign, extent, changeModelName, changeModelVersion);
        ChangeMaskResponse resp = routed.response();

        progressReporter.report(taskId, new ProgressUpdate(1, 1, "UPLOADING"));
        String maskKey = "/%s/change_mask.png".formatted(taskId);
        uploadBase64(resp.maskPngB64(), maskKey);
        String probmapKey = null;
        List<LayerWriteRequest> layers = new ArrayList<>();
        layers.add(new LayerWriteRequest(null, null, LayerType.CHANGE, maskKey, extent));
        if (resp.probmapPngB64() != null) {
            probmapKey = "/%s/probmap.png".formatted(taskId);
            uploadBase64(resp.probmapPngB64(), probmapKey);
            layers.add(new LayerWriteRequest(null, null, LayerType.PROBMAP, probmapKey, extent));
        }
        resultClient.writeLayers(taskId, layers);

        // 变化区域矢量化（FR-1.7；geo_transform 取 infer 响应，缺省按蒙版尺寸推导）
        double[] geoTransform = resp.geoTransform() != null ? resp.geoTransform()
                : deriveGeoTransform(resp.maskPngB64(), extent);
        progressReporter.report(taskId, new ProgressUpdate(1, 1, "VECTORIZING"));
        String regionsFileKey = vectorizeIfEager(taskId,
                List.of(new MaskRef(null, null, LayerType.CHANGE, maskKey, resp.maskPngB64(),
                        geoTransform)), minArea);

        ObjectNode result = MAPPER.createObjectNode();
        result.put("task_id", taskId.toString());
        result.set("geo_extent", extentNode(extent));
        result.put("mask_key", maskKey);
        if (probmapKey != null) {
            result.put("probmap_key", probmapKey);
        }
        if (resp.statistics() != null) {
            result.set("statistics", resp.statistics());
        }
        // 配准校验实测平移量与自动配准标记透传（C-3，评审 D-03；本期 auto_aligned 恒 false）
        if (resp.estimatedShiftPx() != null) {
            result.put("estimated_shift_px", resp.estimatedShiftPx());
        }
        result.put("auto_aligned", resp.autoAligned() != null && resp.autoAligned());
        if (regionsFileKey != null) {
            result.put("regions_file_key", regionsFileKey);
        }
        result.set("inference", inferenceNode(resp, routed.degraded()));
        taskClient.updateStatus(taskId, new StatusUpdate(TaskStatus.SUCCESS, null, null, result));
    }

    // ================= 共享子步骤 =================

    /** 蒙版引用（期次/要素/图层类型/key/base64/geo_transform——base64 供 diff/矢量化直接上行） */
    private record MaskRef(String period, String element, LayerType type, String key,
                           String base64, double[] geoTransform) {
    }

    private record SegOutcome(SegmentationResponse resp, Map<String, String> maskKeys,
                              Map<String, String> masksB64, List<MaskRef> allMasks,
                              String combinedKey, double[] geoTransform, boolean degraded) {
    }

    /** 分割调用（含预签名 URL 过期重签重试一次，J-02/A-3②）+ 蒙版上传 + 图层写入（矢量化由调用方统一收口） */
    private SegOutcome segmentImage(UUID taskId, String imageKey, String period,
                                    List<ElementDto> elements, Integer minArea,
                                    GeoExtent geoExtent, boolean sync) throws IOException {
        // 1. 推理请求组装（class_mapping/colors 自 element_catalog 下发，J-03/J-06/C-2）
        String segModelName = configCache.getOrDefault("inference.local-seg-model-name", "");
        String segModelVersion = configCache.getOrDefault("inference.local-seg-model-version", "");
        Map<String, Integer> classMap = new LinkedHashMap<>();
        Map<String, String> colors = new LinkedHashMap<>();
        elements.forEach(e -> {
            classMap.put(e.id(), e.modelClassId());
            colors.put(e.id(), e.color());
            
        });
        SegmentationRequest req = new SegmentationRequest(signInternal(imageKey),
                extentArray(geoExtent), elements.stream().map(ElementDto::id).toList(),
                classMap, colors, minArea, segModelName, segModelVersion);

        // 2. 推理（三级路由 + 熔断降级，FR-5；同步/异步超时分档，B-2）
        var routed = segmentWithResignRetry(taskId, req, imageKey, sync);
        SegmentationResponse resp = routed.response();

        // 3. geo_transform：取 infer 响应（Python 权威值）；缺省按蒙版像素尺寸推导（remote/降级路径）
        double[] geoTransform = resp.geoTransform() != null ? resp.geoTransform()
                : deriveGeoTransform(firstMaskB64(resp), geoExtent);

        // 4. 蒙版上传（base64 → 存储，评审 R-02）+ 图层写入
        // 防御性过滤：仅接受请求的元素类别（不信任下游返回超集，否则多余对象会逃脱清理，M4 联调实测）
        java.util.Set<String> requested = elements.stream().map(ElementDto::id).collect(java.util.stream.Collectors.toSet());
        List<LayerWriteRequest> layers = new ArrayList<>();
        Map<String, String> maskKeys = new LinkedHashMap<>();
        Map<String, String> masksB64 = new LinkedHashMap<>();
        List<MaskRef> allMasks = new ArrayList<>();
        if (resp.perClassMasks() != null) {
            for (var entry : resp.perClassMasks().entrySet()) {
                if (!requested.contains(entry.getKey())) {
                    log.warn("下游返回了未请求的要素蒙版，丢弃: taskId={}, element={}", taskId, entry.getKey());
                    continue;
                }
                String key = maskKey(taskId, period, entry.getKey());
                uploadBase64(entry.getValue(), key);
                maskKeys.put(entry.getKey(), key);
                masksB64.put(entry.getKey(), entry.getValue());
                layers.add(new LayerWriteRequest(period, entry.getKey(), LayerType.FEATURE, key,
                        geoExtent));
                allMasks.add(new MaskRef(period, entry.getKey(), LayerType.FEATURE, key,
                        entry.getValue(), geoTransform));
            }
        }
        String combinedKey = null;
        if (resp.combinedMaskPngB64() != null && period == null) {
            // 合成蒙版仅单期任务生成（需求 §7.1）
            combinedKey = "/%s/combined_mask.png".formatted(taskId);
            uploadBase64(resp.combinedMaskPngB64(), combinedKey);
        }
        resultClient.writeLayers(taskId, layers);
        return new SegOutcome(resp, maskKeys, masksB64, allMasks, combinedKey, geoTransform,
                routed.degraded());
    }

    /** 预签名 URL 过期（存储端 403）时重签并重试一次（J-02/A-3②；第二次失败按原错误抛出） */
    private com.example.mapchange.analysis.inference.FallbackResult<SegmentationResponse>
            segmentWithResignRetry(UUID taskId, SegmentationRequest req, String imageKey, boolean sync) {
        try {
            return inferenceRouter.segment(req, sync);
        } catch (ImageUrlExpiredException e) {
            log.warn("影像预签名 URL 失效，重签后重试一次: taskId={}", taskId);
            return inferenceRouter.segment(new SegmentationRequest(signInternal(imageKey),
                    req.geoExtent(), req.elements(), req.classMapping(), req.colors(),
                    req.minArea(), req.modelName(), req.modelVersion()), sync);
        }
    }

    /** 变化检测的 URL 过期重签重试（before/after 一并重签，J-02/A-3②） */
    private com.example.mapchange.analysis.inference.FallbackResult<ChangeMaskResponse>
            detectChangeWithResignRetry(UUID taskId, String beforeKey, String afterKey,
                                        Double threshold, Integer minArea, Boolean autoAlign,
                                        GeoExtent extent, String modelName, String modelVersion) {
        try {
            return inferenceRouter.detectChange(new ChangeDetectRequest(signInternal(beforeKey),
                    signInternal(afterKey), extentArray(extent), threshold, minArea, autoAlign,
                    modelName, modelVersion));
        } catch (ImageUrlExpiredException e) {
            log.warn("影像预签名 URL 失效，重签后重试一次: taskId={}", taskId);
            return inferenceRouter.detectChange(new ChangeDetectRequest(signInternal(beforeKey),
                    signInternal(afterKey), extentArray(extent), threshold, minArea, autoAlign,
                    modelName, modelVersion));
        }
    }

    /** 由蒙版 PNG 尺寸 + geo_extent 推导 geo_transform（infer 未返回时的兜底；口径同 Python PNG 线性推导） */
    private double[] deriveGeoTransform(String maskB64, GeoExtent extent) {
        if (maskB64 == null) {
            return null;
        }
        int[] size = PngSizeReader.readSize(Base64.getDecoder().decode(maskB64));
        if (size == null) {
            log.warn("蒙版非 PNG 或头不完整，geo_transform 缺省（下游按像素坐标处理）");
            return null;
        }
        return GeoTransforms.fromExtent(extent, size[0], size[1]);
    }

    private String firstMaskB64(SegmentationResponse resp) {
        return resp.perClassMasks() != null && !resp.perClassMasks().isEmpty()
                ? resp.perClassMasks().values().iterator().next()
                : resp.combinedMaskPngB64();
    }

    private DiffRequest.DiffColors diffColors() {
        return new DiffRequest.DiffColors(
                configCache.getOrDefault("feature.diff-colors.added", "#00FF00"),
                configCache.getOrDefault("feature.diff-colors.removed", "#FF0000"));
    }

    /** 矢量化收口（persist.vectorize=eager 时）：全部蒙版 → regions.geojson；返回 key 或 null */
    private String vectorizeIfEager(UUID taskId, List<MaskRef> masks, Integer minArea) {
        if (masks.isEmpty()
                || !"eager".equals(configCache.getOrDefault("persist.vectorize", "eager"))
                || "none".equals(configCache.getOrDefault("persist.regions", "file"))) {
            return null;
        }
        double minAreaFilter = minArea != null ? minArea
                : Double.parseDouble(configCache.getOrDefault("feature.defaults.min-area", "100"));
        ArrayNode features = MAPPER.createArrayNode();
        for (MaskRef mask : masks) {
            var resp = computeClient.vectorize(new VectorizeRequest(mask.base64(), minAreaFilter,
                    mask.geoTransform()));
            RegionFeatureMapper.toFeatures(resp, mask.period(), mask.element(),
                    RegionFeatureMapper.regionTypeOf(mask.type().name())).forEach(features::add);
        }
        ObjectNode fc = MAPPER.createObjectNode();
        fc.put("type", "FeatureCollection");
        fc.set("features", features);
        return resultClient.writeRegionsFile(taskId, new RegionsFileRequest(fc)).data();
    }

    // ================= 结果组装 =================

    /** 组装要素识别响应（mask_url 实时签名；落库版本只存 key，评审 P-04） */
    private ObjectNode buildExtractionResult(UUID taskId, SegOutcome outcome, String regionsFileKey,
                                             GeoExtent geoExtent, long elapsedMs, boolean signUrls) {
        ObjectNode result = MAPPER.createObjectNode();
        result.put("task_id", taskId.toString());
        result.set("inference", inferenceNode(outcome.resp(), outcome.degraded()));
        result.set("geo_extent", extentNode(geoExtent));
        ArrayNode layers = result.putArray("layers");
        for (var entry : outcome.maskKeys().entrySet()) {
            ObjectNode layer = layers.addObject();
            layer.put("element", entry.getKey());
            layer.put("mask_key", entry.getValue());
            if (signUrls) {
                layer.put("mask_url", storageClient.signUrl(
                        new SignUrlRequest(entry.getValue(), "FRONTEND", null)).data().url());
            }
            JsonNode stat = statisticsOf(outcome.resp().statistics(), entry.getKey());
            if (stat != null) {
                layer.set("statistics", stat);
            }
        }
        if (outcome.combinedKey() != null) {
            result.put("combined_mask_key", outcome.combinedKey());
            if (signUrls) {
                result.put("combined_mask_url", storageClient.signUrl(
                        new SignUrlRequest(outcome.combinedKey(), "FRONTEND", null)).data().url());
            }
        }
        if (regionsFileKey != null) {
            result.put("regions_file_key", regionsFileKey);
        }
        if (elapsedMs > 0) {
            result.put("elapsed_ms", elapsedMs);
        }
        return result;
    }

    /** 推理信息：provider 以 actual_provider 回映射为准（A-1），缺省回退 resolver；模型名/版本取 model_info（FR-3.3） */
    private ObjectNode inferenceNode(SegmentationResponse resp, boolean degraded) {
        return inferenceNode(resp != null ? resp.actualProvider() : null,
                resp != null && resp.modelInfo() != null ? resp.modelInfo().name() : null,
                resp != null && resp.modelInfo() != null ? resp.modelInfo().version() : null,
                degraded);
    }

    private ObjectNode inferenceNode(ChangeMaskResponse resp, boolean degraded) {
        return inferenceNode(resp != null ? resp.actualProvider() : null,
                resp != null && resp.modelInfo() != null ? resp.modelInfo().name() : null,
                resp != null && resp.modelInfo() != null ? resp.modelInfo().version() : null,
                degraded);
    }

    private ObjectNode inferenceNode(String actualProvider, String modelName, String modelVersion,
                                     boolean degraded) {
        ObjectNode inference = MAPPER.createObjectNode();
        String provider = InferenceRouter.fromPythonProvider(actualProvider);
        if (provider == null) {
            provider = resolver.current() != null ? resolver.current().name().toLowerCase() : "unknown";
        }
        inference.put("provider", provider);
        inference.put("model_name", modelName);
        inference.put("model_version", modelVersion);
        inference.put("degraded", degraded);
        return inference;
    }

    private ArrayNode extentNode(GeoExtent extent) {
        return MAPPER.valueToTree(extentArray(extent));
    }

    private double[] extentArray(GeoExtent extent) {
        return new double[]{extent.minx(), extent.miny(), extent.maxx(), extent.maxy()};
    }

    private JsonNode statisticsOf(JsonNode statistics, String element) {
        if (statistics != null && statistics.isObject() && statistics.has(element)) {
            return statistics.get(element);
        }
        return null;
    }

    /** 落库版：剥离签名 URL 字段，仅保留 key（评审 P-04） */
    private ObjectNode maskUrlsAsKeys(ObjectNode result) {
        ObjectNode copy = result.deepCopy();
        copy.remove("combined_mask_url");
        if (copy.path("layers").isArray()) {
            copy.path("layers").forEach(l -> ((ObjectNode) l).remove("mask_url"));
        }
        return copy;
    }

    // ================= 工具 =================

    private ObjectNode basePayload(String imageKey, List<ElementDto> elements,
                                   Integer minArea, GeoExtent geoExtent) {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("image_ref", imageKey);
        ArrayNode els = payload.putArray("elements");
        elements.forEach(e -> els.add(e.id()));
        if (minArea != null) {
            payload.put("min_area", minArea);
        }
        payload.set("geo_extent", extentNode(geoExtent));
        return payload;
    }

    private JsonNode payloadOf(UUID taskId) {
        var task = taskClient.get(taskId).data();
        if (task == null) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "任务读取失败: " + taskId);
        }
        return task.payload();
    }

    /** payload 中的要素 ID 列表 → 目录完整项（真实 model_class_id/color，J-03/J-06 下发前提） */
    private List<ElementDto> readElements(JsonNode payload) {
        List<String> ids = new ArrayList<>();
        payload.path("elements").forEach(e -> ids.add(e.asText()));
        return validator.resolveElements(ids);
    }

    /** geo_extent 兼容数组（需求契约 [minx,miny,maxx,maxy]）与对象两种形态 */
    private GeoExtent readGeoExtent(JsonNode node) {
        try {
            if (node != null && node.isArray() && node.size() == 4) {
                return new GeoExtent(node.get(0).asDouble(), node.get(1).asDouble(),
                        node.get(2).asDouble(), node.get(3).asDouble());
            }
            return MAPPER.treeToValue(node, GeoExtent.class);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INVALID_INPUT, "payload.geo_extent 非法: " + node);
        }
    }

    private String maskKey(UUID taskId, String period, String element) {
        return period != null
                ? "/%s/%s/%s_mask.png".formatted(taskId, period, element)
                : "/%s/%s_mask.png".formatted(taskId, element);
    }

    /** 执行期签发内网预签名 URL（J-02/B-1：Worker 消费后签发，TTL 仅需覆盖执行窗口） */
    private String signInternal(String key) {
        return storageClient.signUrl(new SignUrlRequest(key, "INTERNAL", null)).data().url();
    }

    private void uploadBase64(String base64, String key) {
        byte[] bytes = Base64.getDecoder().decode(base64);
        storageClient.put(key, bytes);
    }

    /** 失败登记（异步任务执行失败时调用） */
    public void markFailed(UUID taskId, String errorCode, String message) {
        try {
            taskClient.updateStatus(taskId, new StatusUpdate(TaskStatus.FAILED, errorCode, message, null));
        } catch (Exception e) {
            log.error("任务失败状态登记失败: id={}, 原因={}", taskId, e.getMessage());
        }
    }

    /** 进入 PROCESSING（Worker 消费时；含取消竞态兜底回查，评审 P-08） */
    public boolean tryStart(UUID taskId) {
        try {
            var resp = taskClient.get(taskId);
            if (resp.data() == null || resp.data().status() != TaskStatus.QUEUED) {
                log.info("任务已不在 QUEUED 态（竞态兜底，丢弃）: id={}, status={}",
                        taskId, resp.data() != null ? resp.data().status() : "null");
                return false;
            }
            taskClient.updateStatus(taskId, new StatusUpdate(TaskStatus.PROCESSING, null, null, null));
            MDC.put("task_id", taskId.toString());
            return true;
        } catch (BizException e) {
            log.info("任务状态流转冲突（丢弃）: id={}, code={}", taskId, e.errorCode());
            return false;
        }
    }

    /** 瞬时错误（重试有价值）：推理超时/不可用/内部错误 */
    private boolean isTransient(ErrorCode code) {
        return code == ErrorCode.INFERENCE_TIMEOUT || code == ErrorCode.INFERENCE_UNAVAILABLE
                || code == ErrorCode.INTERNAL_ERROR;
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
