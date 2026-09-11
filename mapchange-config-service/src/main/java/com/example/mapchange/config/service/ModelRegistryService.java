package com.example.mapchange.config.service;

import com.example.mapchange.common.core.api.BizException;
import com.example.mapchange.common.core.api.ErrorCode;
import com.example.mapchange.common.core.dto.ModelActivateRequest;
import com.example.mapchange.common.core.dto.ModelRegisterRequest;
import com.example.mapchange.common.core.dto.ModelVersionDto;
import com.example.mapchange.config.client.StorageClient;
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
 */
@Service
public class ModelRegistryService {

    private static final Logger log = LoggerFactory.getLogger(ModelRegistryService.class);

    private final ModelRegistryRepository repository;
    private final ConfigService configService;
    private final StorageClient storageClient;
    private final Path sharedDir;

    public ModelRegistryService(ModelRegistryRepository repository, ConfigService configService,
                                StorageClient storageClient,
                                @Value("${models.shared-dir:/models}") String sharedDir) {
        this.repository = repository;
        this.configService = configService;
        this.storageClient = storageClient;
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

    /** 校验存在性 → 更新激活状态（同类型其余 RETIRED）→ 写 L2 配置 → 广播（经 ConfigService.update） */
    @Transactional
    public void activate(ModelActivateRequest r) {
        ModelRegistryEntity target = repository
                .findByModelNameAndModelVersion(r.modelName(), r.modelVersion())
                .orElseThrow(() -> new BizException(ErrorCode.INVALID_INPUT,
                        "模型版本未登记: %s@%s".formatted(r.modelName(), r.modelVersion())));
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

    /** 模型文件下发：storage-service 取出 → 共享卷 {name}/{version}/model.onnx */
    private void deliverToSharedVolume(ModelRegisterRequest r) {
        Path dir = sharedDir.resolve(r.modelName()).resolve(r.modelVersion());
        Path target = dir.resolve("model.onnx");
        try (InputStream in = storageClient.get(r.storageKey()).getInputStream()) {
            Files.createDirectories(dir);
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("model file delivered to shared volume: {}", target);
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR,
                    "模型文件下发失败（storage_key=%s）: %s".formatted(r.storageKey(), e.getMessage()));
        }
    }

    private ModelVersionDto toDto(ModelRegistryEntity e) {
        return new ModelVersionDto(e.getModelName(), e.getModelVersion(), e.getModelType(),
                e.getStorageKey(), e.getStatus(), e.getActivatedAt(), e.getCreatedBy(), e.getCreatedAt());
    }
}
