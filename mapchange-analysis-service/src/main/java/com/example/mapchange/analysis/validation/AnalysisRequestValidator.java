package com.example.mapchange.analysis.validation;

import com.example.mapchange.analysis.client.ConfigClient;
import com.example.mapchange.common.core.api.BizException;
import com.example.mapchange.common.core.api.ErrorCode;
import com.example.mapchange.common.core.dto.ElementDto;
import com.example.mapchange.common.core.geo.GeoExtent;
import com.example.mapchange.common.core.geo.GeoExtentUtils;
import com.example.mapchange.common.core.geo.TileMatrixParam;
import com.example.mapchange.common.core.geo.TileRange;
import com.example.mapchange.common.web.config.RemoteConfigCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分析请求校验链（J-021，设计 §3.1.5/§3.6）：
 * Tika 格式白名单（PNG/JPEG/TIFF/GeoTIFF）、大小限制、要素目录校验（FR-6.6）、
 * tile_range↔geo_extent 一致性、多期 geo_extent 一致性（FR-8.7）、期次顺序与上限（FR-8.1）。
 */
@Component
public class AnalysisRequestValidator {

    private static final Logger log = LoggerFactory.getLogger(AnalysisRequestValidator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Tika TIKA = new Tika();
    private static final Set<String> ALLOWED_MIME =
            Set.of("image/png", "image/jpeg", "image/tiff");

    private final ConfigClient configClient;
    private final RemoteConfigCache configCache;

    /** 要素目录进程内缓存（TTL 由 config-service 侧保证，调用失败时 1min 内沿用） */
    private volatile List<ElementDto> catalogCache = List.of();

    public AnalysisRequestValidator(ConfigClient configClient, RemoteConfigCache configCache) {
        this.configClient = configClient;
        this.configCache = configCache;
    }

    // ---------- 文件 ----------

    /** 影像格式白名单（Tika 探测真实格式）+ 单文件/总大小限制（L2 配置热生效） */
    public void validateImages(List<MultipartFile> images) {
        long maxFileMb = Long.parseLong(configCache.getOrDefault("security.upload.max-file-mb", "100"));
        long maxRequestMb = Long.parseLong(configCache.getOrDefault("security.upload.max-request-mb", "500"));
        long totalBytes = 0;
        for (MultipartFile f : images) {
            if (f.isEmpty()) {
                throw new BizException(ErrorCode.INVALID_INPUT, "存在空影像文件");
            }
            if (f.getSize() > maxFileMb * 1024 * 1024) {
                throw new BizException(ErrorCode.INPUT_TOO_LARGE,
                        "单文件超过 %dMB 限制: %s".formatted(maxFileMb, f.getOriginalFilename()));
            }
            totalBytes += f.getSize();
            String mime = detectMime(f);
            if (!ALLOWED_MIME.contains(mime)) {
                throw new BizException(ErrorCode.INVALID_INPUT,
                        "影像格式不在白名单（PNG/JPEG/TIFF/GeoTIFF），实测: " + mime);
            }
        }
        if (totalBytes > maxRequestMb * 1024 * 1024) {
            throw new BizException(ErrorCode.INPUT_TOO_LARGE,
                    "总上传量超过 %dMB 限制".formatted(maxRequestMb));
        }
    }

    private String detectMime(MultipartFile f) {
        try {
            return TIKA.detect(f.getInputStream(), f.getOriginalFilename());
        } catch (IOException e) {
            throw new BizException(ErrorCode.INVALID_INPUT, "影像读取失败: " + f.getOriginalFilename());
        }
    }

    /** 读取影像尺寸（只读头；读不出返回 null，由下游决定） */
    public int[] readImageSize(MultipartFile f) {
        try (ImageInputStream in = ImageIO.createImageInputStream(f.getInputStream())) {
            var readers = ImageIO.getImageReaders(in);
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                reader.setInput(in);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            }
        } catch (Exception e) {
            log.warn("读取影像尺寸失败（跳过尺寸校验）: {}", e.getMessage());
        }
        return null;
    }

    // ---------- 要素目录 ----------

    /** FR-6.6：类别超出目录返回明确错误，不静默忽略 */
    public List<ElementDto> validateElements(String elements) {
        List<String> requested = parseCsv(elements);
        if (requested.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_INPUT, "elements 不能为空");
        }
        Map<String, ElementDto> catalog = catalog().stream()
                .filter(ElementDto::enabled)
                .collect(Collectors.toMap(ElementDto::id, Function.identity()));
        List<String> unsupported = requested.stream().filter(e -> !catalog.containsKey(e)).toList();
        if (!unsupported.isEmpty()) {
            throw new BizException(ErrorCode.UNSUPPORTED_ELEMENTS,
                    "要素类别超出可识别范围: %s；当前支持: %s".formatted(unsupported, catalog.keySet()));
        }
        return requested.stream().map(catalog::get).toList();
    }

    /** 当前要素目录（供 NLP 解析注入，J-033） */
    public List<ElementDto> currentCatalog() {
        return catalog();
    }

    private List<ElementDto> catalog() {
        try {
            var resp = configClient.elementCatalog();
            if (resp.data() != null) {
                catalogCache = resp.data();
            }
        } catch (Exception e) {
            log.warn("要素目录获取失败，沿用缓存（{} 项）: {}", catalogCache.size(), e.getMessage());
        }
        return catalogCache;
    }

    // ---------- 坐标与瓦片 ----------

    public TileRange parseTileRange(String tileRange) {
        if (tileRange == null || tileRange.isBlank()) {
            return null;   // 非瓦片场景（直传影像）可为空
        }
        try {
            var node = MAPPER.readTree(tileRange);
            return new TileRange(node.get("z").asInt(), node.get("x_min").asInt(),
                    node.get("x_max").asInt(), node.get("y_min").asInt(), node.get("y_max").asInt());
        } catch (Exception e) {
            throw new BizException(ErrorCode.INVALID_INPUT, "tile_range 非法 JSON: " + tileRange);
        }
    }

    public GeoExtent parseGeoExtent(String geoExtent) {
        if (geoExtent == null || geoExtent.isBlank()) {
            throw new BizException(ErrorCode.INVALID_INPUT, "geo_extent 必选");
        }
        try {
            var node = MAPPER.readTree(geoExtent);
            return new GeoExtent(node.get(0).asDouble(), node.get(1).asDouble(),
                    node.get(2).asDouble(), node.get(3).asDouble());
        } catch (Exception e) {
            throw new BizException(ErrorCode.INVALID_INPUT, "geo_extent 非法 JSON: " + geoExtent);
        }
    }

    /** tile_range↔geo_extent 一致性（瓦片场景）；瓦片矩阵参数从配置读取，禁止写死 */
    public void validateExtentConsistency(TileRange tileRange, GeoExtent extent) {
        if (tileRange == null) {
            return;
        }
        GeoExtentUtils.validateConsistency(tileRange, extent, tileMatrixParam());
    }

    private TileMatrixParam tileMatrixParam() {
        try {
            double[] origin = MAPPER.readValue(
                    configCache.getOrDefault("feature.tile-matrix.origin", "[-180,90]"), double[].class);
            double spanBase = Double.parseDouble(configCache.getOrDefault("feature.tile-matrix.span-base", "360"));
            int startLevel = Integer.parseInt(configCache.getOrDefault("feature.tile-matrix.start-level", "1"));
            return new TileMatrixParam(origin, spanBase, startLevel);
        } catch (Exception e) {
            log.warn("瓦片矩阵参数读取失败，用默认值: {}", e.getMessage());
            return TileMatrixParam.defaults();
        }
    }

    // ---------- 多期 ----------

    /** 多期校验（FR-8.1/8.7）：期次数量匹配、时间顺序升序、N 上限 */
    public List<String> validatePeriods(String periods, int imageCount) {
        List<String> periodList = parseCsv(periods);
        if (periodList.size() != imageCount) {
            throw new BizException(ErrorCode.INVALID_INPUT,
                    "期次数量(%d)与影像数量(%d)不一致".formatted(periodList.size(), imageCount));
        }
        int maxPeriods = Integer.parseInt(configCache.getOrDefault("task.temporal-max-periods", "10"));
        if (periodList.size() < 2 || periodList.size() > maxPeriods) {
            throw new BizException(ErrorCode.INVALID_INPUT,
                    "多期期数须在 [2, %d]，实际 %d".formatted(maxPeriods, periodList.size()));
        }
        for (int i = 1; i < periodList.size(); i++) {
            if (periodList.get(i).compareTo(periodList.get(i - 1)) <= 0) {
                throw new BizException(ErrorCode.PERIOD_ORDER_INVALID,
                        "期次须按时间升序：%s 不晚于 %s".formatted(periodList.get(i), periodList.get(i - 1)));
            }
        }
        return periodList;
    }

    private List<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
