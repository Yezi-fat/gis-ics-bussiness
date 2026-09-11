package com.example.mapchange.result.domain;

import com.example.mapchange.common.core.enums.LayerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * 图层表实体（schema result，设计 §4）。
 * storage_key 落库只存 key、不落签名 URL（评审 P-04）；bbox 为图层地理范围。
 */
@Entity
@Table(schema = "result", name = "layer")
public class LayerEntity {

    @Id
    private UUID id;

    /** 逻辑引用 task.task.id（不建跨 schema 外键） */
    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    /** 期次标签，如 2015；单期任务为 NULL */
    private String period;

    /** 要素类别 ID；变化检测任务为 NULL */
    private String element;

    /** FEATURE / DIFF / CHANGE / PROBMAP */
    @Enumerated(EnumType.STRING)
    @Column(name = "layer_type", nullable = false)
    private LayerType layerType;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "bbox_minx")
    private Double bboxMinx;
    @Column(name = "bbox_miny")
    private Double bboxMiny;
    @Column(name = "bbox_maxx")
    private Double bboxMaxx;
    @Column(name = "bbox_maxy")
    private Double bboxMaxy;

    protected LayerEntity() {
    }

    public LayerEntity(UUID taskId, String period, String element, LayerType layerType,
                       String storageKey, Double bboxMinx, Double bboxMiny,
                       Double bboxMaxx, Double bboxMaxy) {
        this.id = UUID.randomUUID();
        this.taskId = taskId;
        this.period = period;
        this.element = element;
        this.layerType = layerType;
        this.storageKey = storageKey;
        this.bboxMinx = bboxMinx;
        this.bboxMiny = bboxMiny;
        this.bboxMaxx = bboxMaxx;
        this.bboxMaxy = bboxMaxy;
    }

    public UUID getId() { return id; }
    public UUID getTaskId() { return taskId; }
    public String getPeriod() { return period; }
    public String getElement() { return element; }
    public LayerType getLayerType() { return layerType; }
    public String getStorageKey() { return storageKey; }
    public Double getBboxMinx() { return bboxMinx; }
    public Double getBboxMiny() { return bboxMiny; }
    public Double getBboxMaxx() { return bboxMaxx; }
    public Double getBboxMaxy() { return bboxMaxy; }
}
