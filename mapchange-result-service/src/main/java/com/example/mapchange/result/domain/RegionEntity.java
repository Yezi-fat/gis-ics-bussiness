package com.example.mapchange.result.domain;

import com.example.mapchange.common.core.enums.RegionType;
import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Type;

import java.util.UUID;

/**
 * 区域表实体（schema result，设计 §4）。
 * 可选表——默认不写入（多边形明细存 regions.geojson）；仅 persist.regions=db 时启用。
 * geojson 存 JSONB；bbox 冗余标量列供范围检索（无 PostGIS 方案，需求 14.5）。
 */
@Entity
@Table(schema = "result", name = "region")
public class RegionEntity {

    @Id
    private UUID id;

    /** 逻辑引用 task.task.id（不建跨 schema 外键） */
    @Column(nullable = false)
    private UUID taskId;

    private String period;

    private String element;

    /** COVERAGE / ADDED / REMOVED / CHANGED */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RegionType regionType;

    @Type(JsonBinaryType.class)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode geojson;

    @Column(name = "area_m2")
    private Double areaM2;

    /** 置信度均值（评审 P-09c） */
    private Double confidenceAvg;

    @Column(name = "centroid_x")
    private Double centroidX;

    @Column(name = "centroid_y")
    private Double centroidY;

    @Column(nullable = false)
    private Double bboxMinx;
    @Column(nullable = false)
    private Double bboxMiny;
    @Column(nullable = false)
    private Double bboxMaxx;
    @Column(nullable = false)
    private Double bboxMaxy;

    protected RegionEntity() {
    }

    public RegionEntity(UUID taskId, String period, String element, RegionType regionType,
                        JsonNode geojson, Double areaM2, Double confidenceAvg,
                        Double centroidX, Double centroidY,
                        double bboxMinx, double bboxMiny, double bboxMaxx, double bboxMaxy) {
        this.id = UUID.randomUUID();
        this.taskId = taskId;
        this.period = period;
        this.element = element;
        this.regionType = regionType;
        this.geojson = geojson;
        this.areaM2 = areaM2;
        this.confidenceAvg = confidenceAvg;
        this.centroidX = centroidX;
        this.centroidY = centroidY;
        this.bboxMinx = bboxMinx;
        this.bboxMiny = bboxMiny;
        this.bboxMaxx = bboxMaxx;
        this.bboxMaxy = bboxMaxy;
    }
}
