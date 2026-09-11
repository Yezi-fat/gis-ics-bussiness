package com.example.mapchange.analysis.api;

import com.example.mapchange.analysis.inference.InferenceProviderResolver;
import com.example.mapchange.analysis.orchestration.AnalysisOrchestrator;
import com.example.mapchange.analysis.validation.AnalysisRequestValidator;
import com.example.mapchange.analysis.client.PythonNlpClient;
import com.example.mapchange.analysis.client.StorageClient;
import com.example.mapchange.common.core.api.ApiResponse;
import com.example.mapchange.common.core.api.BizException;
import com.example.mapchange.common.core.api.ErrorCode;
import com.example.mapchange.common.core.dto.ElementDto;
import com.example.mapchange.common.core.dto.python.NlParseRequest;
import com.example.mapchange.common.core.dto.python.NlParseResponse;
import com.example.mapchange.common.core.enums.InferenceProvider;
import com.example.mapchange.common.core.enums.TaskType;
import com.example.mapchange.common.core.geo.GeoExtent;
import com.example.mapchange.common.core.geo.TileRange;
import com.example.mapchange.common.web.config.RemoteConfigCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * 四类分析接口入口（对外，经 gateway；请求与需求 §7.1~7.5 / 前端文档 §2 契约一致，评审 P-01）。
 * 四类分析接口均为 multipart/form-data 文件直传：影像参数 @RequestPart MultipartFile，
 * 标量参数 @RequestParam；服务内部先经 storage-service 入库，再以 key/预签名 URL 调 Python
 * （设计 §3.2）。小图同步 200 / 大图自动转异步 202（需求 §7.1）。
 * 注：返回类型由 M1 骨架的 ApiResponse 改为 ResponseEntity<ApiResponse<?>> 以区分 200/202
 * （M3 实现期调整，设计 §2.6 已同步）。
 */
@RestController
@RequestMapping("/api/v1")
public class AnalysisController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AnalysisRequestValidator validator;
    private final AnalysisOrchestrator orchestrator;
    private final InferenceProviderResolver resolver;
    private final StorageClient storageClient;
    private final RemoteConfigCache configCache;
    private final PythonNlpClient nlpClient;

    public AnalysisController(AnalysisRequestValidator validator, AnalysisOrchestrator orchestrator,
                              InferenceProviderResolver resolver, StorageClient storageClient,
                              RemoteConfigCache configCache, PythonNlpClient nlpClient) {
        this.validator = validator;
        this.orchestrator = orchestrator;
        this.resolver = resolver;
        this.storageClient = storageClient;
        this.configCache = configCache;
        this.nlpClient = nlpClient;
    }

    /** FR-6 要素识别：小图同步 200，大图自动转异步 202 + task_id */
    @PostMapping(path = "/feature-extraction", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<?>> featureExtraction(
            @RequestPart("image") MultipartFile image,
            @RequestParam("elements") String elements,
            @RequestParam(value = "min_area", required = false) Integer minArea,
            @RequestParam(value = "tile_range", required = false) String tileRange,
            @RequestParam("geo_extent") String geoExtent,
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "anonymous") String userId)
            throws IOException {
        validator.validateImages(List.of(image));
        List<ElementDto> elementDtos = validator.validateElements(elements);
        TileRange tr = validator.parseTileRange(tileRange);
        GeoExtent extent = validator.parseGeoExtent(geoExtent);
        validator.validateExtentConsistency(tr, extent);

        UUID taskId = UUID.randomUUID();
        String imageKey = "/%s/image.png".formatted(taskId);
        storageClient.put(imageKey, image.getBytes());

        if (isSync(image)) {
            return ResponseEntity.ok(ApiResponse.ok(orchestrator.runSyncFeatureExtraction(
                    taskId, imageKey, elementDtos, minArea, extent, userId)));
        }
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("image_ref", imageKey);
        payload.putPOJO("elements", elementDtos.stream().map(ElementDto::id).toList());
        if (minArea != null) {
            payload.put("min_area", minArea);
        }
        payload.putPOJO("geo_extent", new double[]{extent.minx(), extent.miny(), extent.maxx(), extent.maxy()});
        orchestrator.submitAsync(taskId, TaskType.FEATURE_EXTRACTION, payload, userId);
        return accepted(taskId);
    }

    /** FR-7 双期要素差异比对：异步为主（202 + task_id）；M4（J-028）实现编排 */
    @PostMapping(path = "/feature-comparison", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<?>> featureComparison(
            @RequestPart("before") MultipartFile before,
            @RequestPart("after") MultipartFile after,
            @RequestParam("elements") String elements,
            @RequestParam(value = "min_area", required = false) Integer minArea,
            @RequestParam(value = "auto_align", required = false) Boolean autoAlign,
            @RequestParam(value = "tile_range", required = false) String tileRange,
            @RequestParam("geo_extent") String geoExtent,
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "anonymous") String userId)
            throws IOException {
        validator.validateImages(List.of(before, after));
        List<ElementDto> elementDtos = validator.validateElements(elements);
        TileRange tr = validator.parseTileRange(tileRange);
        GeoExtent extent = validator.parseGeoExtent(geoExtent);
        validator.validateExtentConsistency(tr, extent);

        UUID taskId = UUID.randomUUID();
        storageClient.put("/%s/before.png".formatted(taskId), before.getBytes());
        storageClient.put("/%s/after.png".formatted(taskId), after.getBytes());

        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("before_ref", "/%s/before.png".formatted(taskId));
        payload.put("after_ref", "/%s/after.png".formatted(taskId));
        payload.putPOJO("elements", elementDtos.stream().map(ElementDto::id).toList());
        if (minArea != null) {
            payload.put("min_area", minArea);
        }
        if (autoAlign != null) {
            payload.put("auto_align", autoAlign);
        }
        payload.putPOJO("geo_extent", new double[]{extent.minx(), extent.miny(), extent.maxx(), extent.maxy()});
        orchestrator.submitAsync(taskId, TaskType.FEATURE_COMPARISON, payload, userId);
        return accepted(taskId);
    }

    /** FR-8 多期时序分析：恒为异步（202 + task_id）；M4（J-029）实现编排 */
    @PostMapping(path = "/temporal-analysis", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<?>> temporalAnalysis(
            @RequestPart("images") List<MultipartFile> images,
            @RequestParam("periods") String periods,
            @RequestParam("elements") String elements,
            @RequestParam(value = "min_area", required = false) Integer minArea,
            @RequestParam(value = "tile_range", required = false) String tileRange,
            @RequestParam("geo_extent") String geoExtent,
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "anonymous") String userId)
            throws IOException {
        validator.validateImages(images);
        List<ElementDto> elementDtos = validator.validateElements(elements);
        List<String> periodList = validator.validatePeriods(periods, images.size());
        TileRange tr = validator.parseTileRange(tileRange);
        GeoExtent extent = validator.parseGeoExtent(geoExtent);
        validator.validateExtentConsistency(tr, extent);

        UUID taskId = UUID.randomUUID();
        ObjectNode payload = MAPPER.createObjectNode();
        var imagesNode = payload.putArray("images");
        for (int i = 0; i < images.size(); i++) {
            String ref = "/%s/%s.png".formatted(taskId, periodList.get(i));
            storageClient.put(ref, images.get(i).getBytes());
            imagesNode.addObject().put("period", periodList.get(i)).put("ref", ref);
        }
        payload.putPOJO("elements", elementDtos.stream().map(ElementDto::id).toList());
        if (minArea != null) {
            payload.put("min_area", minArea);
        }
        payload.putPOJO("geo_extent", new double[]{extent.minx(), extent.miny(), extent.maxx(), extent.maxy()});
        orchestrator.submitAsync(taskId, TaskType.TEMPORAL_ANALYSIS, payload, userId);
        return accepted(taskId);
    }

    /** FR-1 端到端变化检测：小图可同步；M4（J-030）实现编排 */
    @PostMapping(path = "/change-detection", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<?>> changeDetection(
            @RequestPart("before") MultipartFile before,
            @RequestPart("after") MultipartFile after,
            @RequestParam(value = "threshold", required = false) Double threshold,
            @RequestParam(value = "min_area", required = false) Integer minArea,
            @RequestParam(value = "auto_align", required = false) Boolean autoAlign,
            @RequestParam(value = "tile_range", required = false) String tileRange,
            @RequestParam("geo_extent") String geoExtent,
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "anonymous") String userId)
            throws IOException {
        validator.validateImages(List.of(before, after));
        TileRange tr = validator.parseTileRange(tileRange);
        GeoExtent extent = validator.parseGeoExtent(geoExtent);
        validator.validateExtentConsistency(tr, extent);

        UUID taskId = UUID.randomUUID();
        storageClient.put("/%s/before.png".formatted(taskId), before.getBytes());
        storageClient.put("/%s/after.png".formatted(taskId), after.getBytes());

        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("before_ref", "/%s/before.png".formatted(taskId));
        payload.put("after_ref", "/%s/after.png".formatted(taskId));
        if (threshold != null) {
            payload.put("threshold", threshold);
        }
        if (minArea != null) {
            payload.put("min_area", minArea);
        }
        if (autoAlign != null) {
            payload.put("auto_align", autoAlign);
        }
        payload.putPOJO("geo_extent", new double[]{extent.minx(), extent.miny(), extent.maxx(), extent.maxy()});
        orchestrator.submitAsync(taskId, TaskType.CHANGE_DETECTION, payload, userId);
        return accepted(taskId);
    }

    /** FR-9 自然语言任务解析（纯文本 JSON，非文件上传；Java 仅透传，设计 §5.3；M5 J-033）：
     *  注入当前要素目录供 Python 做要素映射；nlp.enabled=false 或 Python 不可用时返 NLU_UNAVAILABLE */
    @PostMapping("/nl-task/parse")
    public ApiResponse<NlParseResponse> parseNl(@RequestBody NlParseRequest r) {
        if (!Boolean.parseBoolean(configCache.getOrDefault("features.nlp.enabled", "true"))) {
            throw new BizException(ErrorCode.NLU_UNAVAILABLE, "自然语言解析能力已关闭（nlp.enabled=false）");
        }
        if (r == null || r.text() == null || r.text().isBlank()) {
            throw new BizException(ErrorCode.INVALID_INPUT, "text 不能为空");
        }
        NlParseResponse resp = nlpClient.parse(new NlParseRequest(r.text(), validator.currentCatalog()));
        return ApiResponse.ok(resp);
    }

    // ================= 内部 =================

    /** 同步分流：长边 ≤ sync-max-input-size；本地模式长边 > local-max-input-size 报 INPUT_TOO_LARGE（R-04） */
    private boolean isSync(MultipartFile image) {
        int[] size = validator.readImageSize(image);
        if (size == null) {
            return false;   // 读不出尺寸按异步处理
        }
        int longEdge = Math.max(size[0], size[1]);
        int syncMax = Integer.parseInt(configCache.getOrDefault("inference.sync-max-input-size", "1024"));
        InferenceProvider provider = resolver.current();
        if (provider != null && provider != InferenceProvider.REMOTE) {
            int localMax = Integer.parseInt(configCache.getOrDefault("inference.local-max-input-size", "2048"));
            if (longEdge > localMax) {
                throw new BizException(ErrorCode.INPUT_TOO_LARGE,
                        "本地推理模式输入上限 %dpx，实际 %dpx".formatted(localMax, longEdge));
            }
        }
        return longEdge <= syncMax;
    }

    private ResponseEntity<ApiResponse<?>> accepted(UUID taskId) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("task_id", taskId.toString());
        body.put("status", "QUEUED");
        body.put("poll_hint_ms",
                Long.parseLong(configCache.getOrDefault("task.poll-hint-ms", "2000")));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(body));
    }
}
