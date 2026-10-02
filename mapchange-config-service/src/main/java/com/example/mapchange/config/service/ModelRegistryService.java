package com.example.mapchange.config.service;

import com.example.mapchange.common.core.api.BizException;
import com.example.mapchange.common.core.api.ErrorCode;
import com.example.mapchange.common.core.dto.ModelActivateRequest;
import com.example.mapchange.common.core.dto.ModelRegisterRequest;
import com.example.mapchange.common.core.dto.ModelVersionDto;
import com.example.mapchange.common.core.dto.python.InferModelListResponse;
import com.example.mapchange.config.client.AnalysisClient;
import com.example.mapchange.config.client.StorageClient;
import com.example.mapchange.config.domain.ElementCatalogEntity;
import com.example.mapchange.config.domain.ElementCatalogRepository;
import com.example.mapchange.config.domain.ModelRegistryEntity;
import com.example.mapchange.config.domain.ModelRegistryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 模型版本管理（FR-3.5，评审 P-02/S-03，J-034）。
 * 切换生效链路：激活 = 更新激活状态 + 按 model_type 写对应 L2 键对
 * （SEG → inference.local-seg-model-name/local-seg-model-version；
 *   CHANGE → inference.local-change-model-name/local-change-model-version）
 * → RabbitMQ 配置广播热生效（§3.3）→ analysis-service 后续推理请求携带新模型名/版本 →
 * infer-service 按名+版本从本地模型目录加载。
 * 文件下发（评审 R-03）：登记时经 storage-service 取出写入共享卷 {name}/{version}/。
 * 激活前置校验（对接事项 J-2，Function-Q3）：与 infer-service /infer/models 对账——
 * 模型缺失或 fp32 产物缺失拒绝激活；int8 缺失、目录映射超类别表记 WARN。
 */
@Service
public class ModelRegistryService {

    private static final Logger log = LoggerFactory.getLogger(ModelRegistryService.class);

    private final ModelRegistryRepository repository;
    private final ConfigService configService;
    private final StorageClient storageClient;
    private final AnalysisClient analysisClient;
    private final ElementCatalogRepository elementCatalogRepository;
    private final Path sharedDir;

    public ModelRegistryService(ModelRegistryRepository repository, ConfigService configService,
                                StorageClient storageClient, AnalysisClient analysisClient,
                                ElementCatalogRepository elementCatalogRepository,
                                @Value("${models.shared-dir:/models}") String sharedDir) {
        this.repository = repository;
        this.configService = configService;
        this.storageClient = storageClient;
        this.analysisClient = analysisClient;
        this.elementCatalogRepository = elementCatalogRepository;
        this.sharedDir = Path.of(sharedDir);
    }

    /** model_registry 表查询（按类型/名称排序） */
    public List<ModelVersionDto> list() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    /** 入库 REGISTERED（模型文件经 storage-service 取出 → 写入共享卷，设计 §3.7） */
    @Transactional
    public ModelVersionDto register(ModelRegisterRequest r, String operator) {
        if (!"SEG".equals(r.modelType()) && !"CHANGE".equals(r.modelType())) {
            throw new BizException(ErrorCode.INVALID_INPUT, "model_type 须为 SEG / CHANGE");
        }
        if (r.modelName() == null || r.modelName().isBlank()
                || r.modelVersion() == null || r.modelVersion().isBlank()
                || r.storageKey() == null || r.storageKey().isBlank()) {
            throw new BizException(ErrorCode.INVALID_INPUT, "model_name/model_version/storage_key 必填");
        }
        if (repository.findByModelNameAndModelVersion(r.modelName(), r.modelVersion()).isPresent()) {
            throw new BizException(ErrorCode.INVALID_INPUT,
                    "模型版本已存在: %s@%s".formatted(r.modelName(), r.modelVersion()));
        }
        deliverToSharedVolume(r);
        ModelRegistryEntity saved = repository.save(new ModelRegistryEntity(
                r.modelName(), r.modelVersion(), r.modelType(), r.storageKey(), operator));
        log.info("model registered: {}@{} ({})", r.modelName(), r.modelVersion(), r.modelType());
        return toDto(saved);
    }

    /** 校验存在性 → 推理服务对账前置校验（J-2）→ 更新激活状态（同类型其余 RETIRED）→ 写 L2 配置 → 广播（经 ConfigService.update） */
    @Transactional
    public void activate(ModelActivateRequest r) {
        ModelRegistryEntity target = repository
                .findByModelNameAndModelVersion(r.modelName(), r.modelVersion())
                .orElseThrow(() -> new BizException(ErrorCode.INVALID_INPUT,
                        "模型版本未登记: %s@%s".formatted(r.modelName(), r.modelVersion())));
        validateAgainstInfer(target);
        // 同类型其余置 RETIRED，目标置 ACTIVE
        repository.findByModelType(target.getModelType()).forEach(e -> {
            boolean isTarget = e.getId().equals(target.getId());
            e.setStatus(isTarget ? "ACTIVE" : "RETIRED");
            e.setActivatedAt(isTarget ? Instant.now() : e.getActivatedAt());
            repository.save(e);
        });
        // 按 model_type 写对应 L2 键对（ConfigService.update 内含校验/审计/广播）
        Map<String, String> kv = "SEG".equals(target.getModelType())
                ? Map.of("inference.local-seg-model-name", target.getModelName(),
                        "inference.local-seg-model-version", target.getModelVersion())
                : Map.of("inference.local-change-model-name", target.getModelName(),
                        "inference.local-change-model-version", target.getModelVersion());
        configService.update("inference", kv, "model-admin");
        log.info("model activated: {}@{} ({})", target.getModelName(), target.getModelVersion(),
                target.getModelType());
    }

    /**
     * 模型文件下发（J-05/A-2）：storage_key 两种形态——
     * ① 目录前缀（推荐，FR-3.2 双产物）：其下固定两对象 {storage_key}/fp32.onnx 与
     *   {storage_key}/int8.onnx，**均为必需**（infer-service 加载时两件齐备校验，缺件即
     *   MODEL_NOT_READY，2026-09-12 联调实测），缺任一件拒绝登记；
     * ② 单文件 .onnx（兼容旧登记）：视作 fp32 单产物下发，记 WARN（Python 侧将因缺 int8 不可加载，
     *   仅供元数据登记/老版本回滚场景）。
     * 下发目标固定命名 {sharedDir}/{name}/{version}/fp32.onnx|int8.onnx（infer-service 按名+版本懒加载）。
     */
    private void deliverToSharedVolume(ModelRegisterRequest r) {
        Path dir = sharedDir.resolve(r.modelName()).resolve(r.modelVersion());
        String key = r.storageKey();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR,
                    "模型目录创建失败（%s）: %s".formatted(dir, e.getMessage()));
        }
        if (key.endsWith(".onnx")) {
            copyFromStorage(key, dir.resolve("fp32.onnx"));
            log.warn("单产物登记（缺 int8.onnx，infer-service 将无法加载该版本，仅供元数据登记）: {}@{}",
                    r.modelName(), r.modelVersion());
            return;
        }
        String prefix = key.endsWith("/") ? key : key + "/";
        copyFromStorage(prefix + "fp32.onnx", dir.resolve("fp32.onnx"));
        copyFromStorage(prefix + "int8.onnx", dir.resolve("int8.onnx"));
    }

    private void copyFromStorage(String storageKey, Path target) {
        try (InputStream in = storageClient.get(storageKey).getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("model file delivered to shared volume: {}", target);
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR,
                    "模型文件下发失败（storage_key=%s）: %s".formatted(storageKey, e.getMessage()));
        } catch (BizException e) {
            throw new BizException(ErrorCode.INVALID_INPUT,
                    "模型产物不存在或不可读（storage_key=%s）".formatted(storageKey));
        }
    }

    private ModelVersionDto toDto(ModelRegistryEntity e) {
        return new ModelVersionDto(e.getModelName(), e.getModelVersion(), e.getModelType(),
                e.getStorageKey(), e.getStatus(), e.getActivatedAt(), e.getCreatedBy(), e.getCreatedAt());
    }

    /**
     * 推理服务实际可用模型清单（对接事项 J-2，经 analysis-service 透传 infer /infer/models）；
     * 供模型管理页"推理服务实际可用模型"区块展示。infer 不可达抛 INFERENCE_UNAVAILABLE（管理端明确报错）
     */
    public InferModelListResponse availableModels() {
        try {
            InferModelListResponse data = analysisClient.models().data();
            if (data == null) {
                throw new BizException(ErrorCode.INFERENCE_UNAVAILABLE, "推理服务模型清单返回为空");
            }
            return data;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.INFERENCE_UNAVAILABLE,
                    "推理服务模型清单获取失败: " + e.getMessage());
        }
    }

    /**
     * 激活前置校验（J-2）：与 infer /infer/models 对账——
     * ① 模型版本不在清单中 → 拒绝（激活后必然推理失败，MODEL_NOT_READY）；
     * ② fp32 产物缺失 → 拒绝；int8 缺失 → WARN（量化通道不可用）；
     * ③ SEG 类型：启用要素的 model_class_id 须 < class_count-1（检测模型末位通道为背景），超范围 WARN。
     * infer 不可达时记 WARN 跳过（不阻断激活，推理期 Python 兜底报错）。
     */
    private void validateAgainstInfer(ModelRegistryEntity target) {
        InferModelListResponse available;
        try {
            available = analysisClient.models().data();
        } catch (Exception e) {
            log.warn("激活前置校验：推理服务模型清单不可用，跳过对账（%s@%s）: %s"
                    .formatted(target.getModelName(), target.getModelVersion(), e.getMessage()));
            return;
        }
        if (available == null || available.models() == null) {
            log.warn("激活前置校验：推理服务模型清单为空，跳过对账（{}@{}）",
                    target.getModelName(), target.getModelVersion());
            return;
        }
        var version = available.models().stream()
                .filter(m -> target.getModelName().equals(m.name()) && m.versions() != null)
                .flatMap(m -> m.versions().stream())
                .filter(v -> target.getModelVersion().equals(v.version()))
                .findFirst();
        if (version.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_INPUT,
                    "推理服务模型目录中不存在 %s@%s（与 /infer/models 对账失败），请核对名称/版本或先完成登记下发"
                            .formatted(target.getModelName(), target.getModelVersion()));
        }
        var v = version.get();
        if (!v.fp32()) {
            throw new BizException(ErrorCode.INVALID_INPUT,
                    "模型 %s@%s 缺少 fp32 产物，infer-service 无法加载，拒绝激活"
                            .formatted(target.getModelName(), target.getModelVersion()));
        }
        if (!v.int8()) {
            log.warn("模型 {}@{} 缺少 int8 产物（量化推理通道不可用）", target.getModelName(),
                    target.getModelVersion());
        }
        if ("SEG".equals(target.getModelType()) && v.classCount() != null) {
            int maxValidClassId = v.classCount() - 2;   // 检测模型末位通道为背景（对接事项 J-1 约束）
            List<String> outOfRange = elementCatalogRepository.findAll().stream()
                    .filter(ElementCatalogEntity::isEnabled)
                    .filter(e -> e.getModelClassId() > maxValidClassId)
                    .map(ElementCatalogEntity::getId)
                    .toList();
            if (!outOfRange.isEmpty()) {
                log.warn("要素目录存在超出模型类别范围的映射（模型 {}@{} 有效 model_class_id 0~{}）: {}",
                        target.getModelName(), target.getModelVersion(), maxValidClassId, outOfRange);
            }
        }
    }
}
