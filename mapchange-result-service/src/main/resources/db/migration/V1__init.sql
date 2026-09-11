-- result-service 初始化（schema result，设计 §4）
CREATE TABLE IF NOT EXISTS result.layer (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id         UUID NOT NULL,               -- 逻辑引用 task.task.id（不建跨 schema 外键）
    period          VARCHAR(16),                 -- 期次标签，如 2015；单期任务为 NULL
    element         VARCHAR(32),                 -- 要素类别 ID；变化检测任务为 NULL
    layer_type      VARCHAR(24) NOT NULL,        -- FEATURE / DIFF / CHANGE / PROBMAP
    storage_key     VARCHAR(512) NOT NULL,       -- 落库只存 key、不落签名 URL（评审 P-04）
    bbox_minx       DOUBLE PRECISION,
    bbox_miny       DOUBLE PRECISION,
    bbox_maxx       DOUBLE PRECISION,
    bbox_maxy       DOUBLE PRECISION
);
CREATE INDEX IF NOT EXISTS idx_layer_task ON result.layer(task_id);

-- 区域表（可选，仅 persist.regions=db 时启用；默认多边形明细存 regions.geojson）
CREATE TABLE IF NOT EXISTS result.region (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id         UUID NOT NULL,               -- 逻辑引用 task.task.id
    period          VARCHAR(16),
    element         VARCHAR(32),
    region_type     VARCHAR(16) NOT NULL,        -- COVERAGE / ADDED / REMOVED / CHANGED
    geojson         JSONB NOT NULL,
    area_m2         DOUBLE PRECISION,
    confidence_avg  DOUBLE PRECISION,            -- 置信度均值（评审 P-09c）
    centroid_x      DOUBLE PRECISION,
    centroid_y      DOUBLE PRECISION,
    bbox_minx       DOUBLE PRECISION NOT NULL,
    bbox_miny       DOUBLE PRECISION NOT NULL,
    bbox_maxx       DOUBLE PRECISION NOT NULL,
    bbox_maxy       DOUBLE PRECISION NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_region_bbox ON result.region(bbox_minx, bbox_miny, bbox_maxx, bbox_maxy);
CREATE INDEX IF NOT EXISTS idx_region_task ON result.region(task_id);
