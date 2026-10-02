package com.example.mapchange.common.core.dto.python;

import java.util.List;

/**
 * infer-service 模型发现响应（《Python推理计算服务接口文档》V1.1 §1.4，GET /infer/models，
 * 对接事项 J-2 / Function-Q3）。
 * 字段用途：fp32/int8 双产物齐全性（登记/激活校验）；class_labels 类别名称表（索引=类别 ID）；
 * class_count 概率图通道数（检测模型末位为背景，model_class_id < class_count-1）；
 * loaded 会话是否常驻（运维诊断）；default_for 该版本承担的默认角色（segmentation/change_detection）。
 * 容错：Python v1.0.0 起 class_labels/class_count 在模型未内嵌 names 且未配置时可能为 null，勿按非空断言。
 */
public record InferModelListResponse(String modelsDir, List<InferModel> models) {

    public record InferModel(String name, List<InferModelVersion> versions) {
    }

    public record InferModelVersion(String version, boolean fp32, boolean int8,
                                    List<String> classLabels, Integer classCount,
                                    boolean loaded, List<String> defaultFor) {
    }
}
