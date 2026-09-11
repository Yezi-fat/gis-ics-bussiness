-- task-service 初始化（schema task，设计 §4）
CREATE TABLE IF NOT EXISTS task.task (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_type       VARCHAR(32) NOT NULL,        -- FEATURE_EXTRACTION / FEATURE_COMPARISON / TEMPORAL_ANALYSIS / CHANGE_DETECTION
    status          VARCHAR(16) NOT NULL,        -- QUEUED / PROCESSING / SUCCESS / FAILED / CANCELLED
    progress        JSONB,                       -- {total_periods, completed_periods, stage}
    payload         JSONB NOT NULL,              -- 任务参数快照（影像引用、要素类别、阈值等）
    provider        VARCHAR(16),                 -- REMOTE / LOCAL_GPU / LOCAL_CPU
    model_name      VARCHAR(128),
    model_version   VARCHAR(64),
    degraded        BOOLEAN DEFAULT FALSE,       -- 降级标记（FR-3.3）
    result          JSONB,                       -- 统计/图层索引/面积序列/regions_file_key（只存 storage key，评审 P-04）
    error_code      VARCHAR(48),
    error_message   TEXT,
    created_by      VARCHAR(64),
    created_at      TIMESTAMPTZ DEFAULT now(),
    finished_at     TIMESTAMPTZ,
    version         INT DEFAULT 0                -- 乐观锁
);
CREATE INDEX IF NOT EXISTS idx_task_status_created ON task.task(status, created_at);
CREATE INDEX IF NOT EXISTS idx_task_type_created   ON task.task(task_type, created_at DESC);
