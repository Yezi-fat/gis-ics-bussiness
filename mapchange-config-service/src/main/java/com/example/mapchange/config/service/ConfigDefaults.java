package com.example.mapchange.config.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * L2 运行配置 + L3 功能开关的默认注册表（设计 §7 全量配置项清单，FR-10.1/10.4）。
 * 启动种子写入（仅首次，幂等）与恢复默认值（reset）均以本表为准；ConfigValidator 的
 * 类型/范围/枚举规则也出自本表。
 */
public final class ConfigDefaults {

    /** 配置项定义：类型/取值约束供 ConfigValidator 使用 */
    public record ConfigDef(String key, String group, Type type, String defaultValue,
                            Double min, Double max, List<String> allowed) {
        public enum Type {STRING, INT, DOUBLE, BOOL, DURATION, URL_OR_EMPTY, COLOR, JSON}
    }

    private static ConfigDef def(String key, String group, ConfigDef.Type type, String def) {
        return new ConfigDef(key, group, type, def, null, null, null);
    }

    private static ConfigDef num(String key, String group, ConfigDef.Type type, String def,
                                 double min, double max) {
        return new ConfigDef(key, group, type, def, min, max, null);
    }

    private static ConfigDef enm(String key, String group, String def, String... allowed) {
        return new ConfigDef(key, group, ConfigDef.Type.STRING, def, null, null, List.of(allowed));
    }

    /** 全量配置项（设计 §7 L2 表 + L3 开关表） */
    public static final List<ConfigDef> ALL = List.of(
            // ---- inference ----
            enm("inference.provider", "inference", "auto", "auto", "remote", "local-gpu", "local-cpu"),
            def("inference.fallback-to-local", "inference", ConfigDef.Type.BOOL, "true"),
            def("inference.remote.endpoint", "inference", ConfigDef.Type.URL_OR_EMPTY, ""),
            def("inference.remote.model-name", "inference", ConfigDef.Type.STRING, ""),
            def("inference.remote.model-version", "inference", ConfigDef.Type.STRING, ""),
            def("inference.local-seg-model-name", "inference", ConfigDef.Type.STRING, ""),
            def("inference.local-seg-model-version", "inference", ConfigDef.Type.STRING, ""),
            def("inference.local-change-model-name", "inference", ConfigDef.Type.STRING, ""),
            def("inference.local-change-model-version", "inference", ConfigDef.Type.STRING, ""),
            num("inference.remote.timeout-ms", "inference", ConfigDef.Type.INT, "30000", 1000, 300000),
            num("inference.remote.max-retries", "inference", ConfigDef.Type.INT, "2", 0, 5),
            num("inference.local-max-input-size", "inference", ConfigDef.Type.INT, "2048", 256, 16384),
            num("inference.sync-max-input-size", "inference", ConfigDef.Type.INT, "1024", 256, 16384),
            // ---- task ----
            num("task.worker-prefetch", "task", ConfigDef.Type.INT, "2", 1, 16),
            num("task.temporal-max-periods", "task", ConfigDef.Type.INT, "10", 2, 50),
            num("task.poll-hint-ms", "task", ConfigDef.Type.INT, "2000", 500, 60000),
            // ---- persist ----
            enm("persist.regions", "persist", "file", "file", "db", "none"),
            enm("persist.vectorize", "persist", "eager", "eager", "lazy"),
            num("persist.result-retention-days", "persist", ConfigDef.Type.INT, "90", 1, 3650),
            // ---- storage ----
            def("storage.sign-url-ttl", "storage", ConfigDef.Type.DURATION, "PT2H"),
            def("storage.sign-url-internal-ttl", "storage", ConfigDef.Type.DURATION, "PT10M"),
            num("storage.guard.warn-threshold", "storage", ConfigDef.Type.DOUBLE, "0.8", 0.1, 0.99),
            num("storage.guard.hard-limit", "storage", ConfigDef.Type.DOUBLE, "0.9", 0.1, 0.99),
            // ---- feature ----
            num("feature.defaults.threshold", "feature", ConfigDef.Type.DOUBLE, "0.5", 0.0, 1.0),
            num("feature.defaults.min-area", "feature", ConfigDef.Type.INT, "100", 0, 1000000),
            def("feature.diff-colors.added", "feature", ConfigDef.Type.COLOR, "#00FF00"),
            def("feature.diff-colors.removed", "feature", ConfigDef.Type.COLOR, "#FF0000"),
            def("feature.tile-matrix.origin", "feature", ConfigDef.Type.JSON, "[-180,90]"),
            num("feature.tile-matrix.span-base", "feature", ConfigDef.Type.DOUBLE, "360", 1, 360),
            num("feature.tile-matrix.start-level", "feature", ConfigDef.Type.INT, "1", 0, 24),
            // ---- security ----
            num("security.rate-limit.sync-per-min", "security", ConfigDef.Type.INT, "10", 1, 10000),
            num("security.upload.max-file-mb", "security", ConfigDef.Type.INT, "100", 1, 2048),
            num("security.upload.max-request-mb", "security", ConfigDef.Type.INT, "500", 1, 10240),
            // ---- cache ----
            num("cache.element-catalog-ttl-min", "cache", ConfigDef.Type.INT, "5", 1, 1440),
            // ---- features（L3 功能开关） ----
            def("features.nlp.enabled", "features", ConfigDef.Type.BOOL, "true"),
            def("features.change-detection.enabled", "features", ConfigDef.Type.BOOL, "true"),
            def("features.export-geojson.enabled", "features", ConfigDef.Type.BOOL, "true"),
            def("features.websocket.enabled", "features", ConfigDef.Type.BOOL, "false")
    );

    public static final Map<String, ConfigDef> BY_KEY =
            ALL.stream().collect(Collectors.toUnmodifiableMap(ConfigDef::key, Function.identity()));

    public static List<ConfigDef> ofGroup(String group) {
        return ALL.stream().filter(d -> d.group().equals(group)).toList();
    }

    public static Optional<ConfigDef> find(String key) {
        return Optional.ofNullable(BY_KEY.get(key));
    }

    public static List<String> groups() {
        return ALL.stream().map(ConfigDef::group).distinct().toList();
    }

    private ConfigDefaults() {
    }
}
