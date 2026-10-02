package com.example.mapchange.config.service;

import com.example.mapchange.common.core.dto.ElementDto;
import com.example.mapchange.common.web.config.RemoteConfigCache;
import com.example.mapchange.config.domain.ElementCatalogEntity;
import com.example.mapchange.config.domain.ElementCatalogRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 要素类别目录（FR-6.2）：element_catalog 读写 + Caffeine 缓存。
 * TTL 走 L2 运行配置 cache.element-catalog-ttl-min（默认 5min，热生效——按条目过期时间动态计算）。
 * 种子数据为 EuroSAT 10 类（与当前激活模型 landcover-yolo-v11/v1.0 类别表一致，
 * model_class_id=类别索引；Python 概率图通道 10 为背景，禁映射），随服务首次启动写入（幂等）；
 * 既有部署由 Flyway V2 迁移对齐（2026-10-02，bug-2026-09-28 Function-Q2 / 对接事项 J-1）。
 */
@Service
public class ElementCatalogService {

    private static final Logger log = LoggerFactory.getLogger(ElementCatalogService.class);

    /** 种子数据（FR-6.2；EuroSAT 10 类，modelClassId=模型输出类别索引，索引=模型 class_labels 下标） */
    private static final List<ElementCatalogEntity> SEED = List.of(
            new ElementCatalogEntity("annual_crop", "一年生作物", "#FFD700", 0, true),
            new ElementCatalogEntity("forest", "森林", "#228B22", 1, true),
            new ElementCatalogEntity("herbaceous_vegetation", "草本植被", "#9ACD32", 2, true),
            new ElementCatalogEntity("highway", "公路", "#696969", 3, true),
            new ElementCatalogEntity("industrial", "工业区", "#CD853F", 4, true),
            new ElementCatalogEntity("pasture", "牧场", "#6B8E23", 5, true),
            new ElementCatalogEntity("permanent_crop", "多年生作物", "#DAA520", 6, true),
            new ElementCatalogEntity("residential", "住宅区", "#DC143C", 7, true),
            new ElementCatalogEntity("river", "河流", "#1E90FF", 8, true),
            new ElementCatalogEntity("sea_lake", "海洋湖泊", "#00CED1", 9, true)
    );

    private static final String CACHE_KEY = "all";

    private final ElementCatalogRepository repository;
    private final RemoteConfigCache configCache;
    private final Cache<String, List<ElementDto>> cache;

    public ElementCatalogService(ElementCatalogRepository repository, RemoteConfigCache configCache) {
        this.repository = repository;
        this.configCache = configCache;
        // 动态 TTL：每次计算过期时间时读取当前配置值（热生效）
        this.cache = Caffeine.newBuilder().expireAfter(new Expiry<String, List<ElementDto>>() {
            @Override
            public long expireAfterCreate(String key, List<ElementDto> value, long currentTime) {
                return ttlNanos();
            }

            @Override
            public long expireAfterUpdate(String key, List<ElementDto> value, long currentTime,
                                          long currentDuration) {
                return ttlNanos();
            }

            @Override
            public long expireAfterRead(String key, List<ElementDto> value, long currentTime,
                                        long currentDuration) {
                return currentDuration;
            }
        }).build();
    }

    /** 启动种子写入（幂等：已存在则跳过） */
    @EventListener(ApplicationReadyEvent.class)
    public void seedOnStartup() {
        int seeded = 0;
        for (ElementCatalogEntity e : SEED) {
            if (!repository.existsById(e.getId())) {
                repository.save(e);
                seeded++;
            }
        }
        log.info("element catalog seed done, seeded={}, total={}", seeded, SEED.size());
    }

    /** 全部要素（含禁用）；带缓存 */
    public List<ElementDto> listAll() {
        List<ElementDto> cached = cache.getIfPresent(CACHE_KEY);
        if (cached != null) {
            return cached;
        }
        List<ElementDto> all = repository.findAll().stream()
                .map(e -> new ElementDto(e.getId(), e.getName(), e.getColor(), e.getModelClassId(), e.isEnabled()))
                .toList();
        cache.put(CACHE_KEY, all);
        return all;
    }

    /** 对外目录（仅启用，前端渲染选择器） */
    public List<ElementDto> listEnabled() {
        return listAll().stream().filter(ElementDto::enabled).toList();
    }

    /** 新增/更新要素类别（改库后失效缓存） */
    public ElementDto upsert(ElementDto dto) {
        repository.save(new ElementCatalogEntity(dto.id(), dto.name(), dto.color(),
                dto.modelClassId(), dto.enabled()));
        cache.invalidate(CACHE_KEY);
        return dto;
    }

    private long ttlNanos() {
        String v = configCache.getOrDefault("cache.element-catalog-ttl-min", "5");
        try {
            return Duration.ofMinutes(Long.parseLong(v)).toNanos();
        } catch (NumberFormatException e) {
            return Duration.ofMinutes(5).toNanos();
        }
    }
}
