-- config-service 初始化（schema config，设计 §4）
CREATE TABLE IF NOT EXISTS config.element_catalog (
    id              VARCHAR(32) PRIMARY KEY,     -- forest / grassland / snow / building ...
    name            VARCHAR(64) NOT NULL,
    color           VARCHAR(9) NOT NULL,         -- #RRGGBB[AA]
    model_class_id  INT NOT NULL,                -- 分割模型输出类别 ID 映射（FR-6.2）
    enabled         BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS config.app_config (
    config_key   VARCHAR(128) PRIMARY KEY,       -- 如 inference.provider
    config_group VARCHAR(32) NOT NULL,           -- inference / task / storage / persist / feature / ui
    value        TEXT NOT NULL,
    updated_by   VARCHAR(64),
    updated_at   TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS config.config_audit (
    id           BIGSERIAL PRIMARY KEY,
    config_key   VARCHAR(128) NOT NULL,
    old_value    TEXT,
    new_value    TEXT,
    operator     VARCHAR(64),
    created_at   TIMESTAMPTZ DEFAULT now()
);

-- 本地模型版本注册表（FR-3.5，评审 P-02）：Java 侧只做登记与激活，文件本体由 infer-service 加载（§3.7）
CREATE TABLE IF NOT EXISTS config.model_registry (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_name    VARCHAR(128) NOT NULL,       -- landcover-seg / change-detection
    model_version VARCHAR(64)  NOT NULL,
    model_type    VARCHAR(16)  NOT NULL,       -- SEG / CHANGE
    storage_key   VARCHAR(512) NOT NULL,       -- 模型文件（.onnx）存储位置
    status        VARCHAR(16)  NOT NULL,       -- REGISTERED / ACTIVE / RETIRED
    activated_at  TIMESTAMPTZ,
    created_by    VARCHAR(64),
    created_at    TIMESTAMPTZ DEFAULT now(),
    UNIQUE (model_name, model_version)
);
