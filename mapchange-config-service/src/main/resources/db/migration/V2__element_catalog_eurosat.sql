-- 要素目录重映射为 EuroSAT 10 类（2026-10-02，bug-2026-09-28 Function-Q2 / 对接事项 J-1）
-- 当前激活模型 landcover-yolo-v11/v1.0 为 EuroSAT 检测模型，model_class_id=类别索引（0~9）；
-- 概率图通道 10 为背景，禁映射。旧 4 类目录（grassland/snow/building 等）不再适用，删除；
-- forest 保留但按 EuroSAT 口径刷新名称/颜色。历史任务结果中的旧要素 id 仅存于结果 JSON，不受影响。
INSERT INTO config.element_catalog (id, name, color, model_class_id, enabled) VALUES
    ('annual_crop',           '一年生作物', '#FFD700', 0, TRUE),
    ('forest',                '森林',       '#228B22', 1, TRUE),
    ('herbaceous_vegetation', '草本植被',   '#9ACD32', 2, TRUE),
    ('highway',               '公路',       '#696969', 3, TRUE),
    ('industrial',            '工业区',     '#CD853F', 4, TRUE),
    ('pasture',               '牧场',       '#6B8E23', 5, TRUE),
    ('permanent_crop',        '多年生作物', '#DAA520', 6, TRUE),
    ('residential',           '住宅区',     '#DC143C', 7, TRUE),
    ('river',                 '河流',       '#1E90FF', 8, TRUE),
    ('sea_lake',              '海洋湖泊',   '#00CED1', 9, TRUE)
ON CONFLICT (id) DO UPDATE SET
    name           = EXCLUDED.name,
    color          = EXCLUDED.color,
    model_class_id = EXCLUDED.model_class_id,
    enabled        = EXCLUDED.enabled;

DELETE FROM config.element_catalog
WHERE id NOT IN ('annual_crop', 'forest', 'herbaceous_vegetation', 'highway', 'industrial',
                 'pasture', 'permanent_crop', 'residential', 'river', 'sea_lake');
