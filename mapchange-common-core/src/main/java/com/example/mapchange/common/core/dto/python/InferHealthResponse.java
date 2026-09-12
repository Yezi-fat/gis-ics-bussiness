package com.example.mapchange.common.core.dto.python;

/**
 * infer-service /health 上报（《Python推理计算服务接口文档》§1.3，FR-5.5/5.7）。
 * gpu_available（provider 列表存在）与 gpu_usable（试建会话通过）区分上报——三级解析以 gpu_usable 为准；
 * models 按模型类型键控（segmentation/change_detect）。
 */
public record InferHealthResponse(String status, boolean gpuAvailable, boolean gpuUsable,
                                  Long vramMb, Integer cpuCores, InferModelsHealth models,
                                  boolean remoteConfigured) {

    public record InferModelsHealth(ModelHealth segmentation, ModelHealth changeDetect) {
    }

    public record ModelHealth(boolean loaded, String version, String provider,
                              java.util.List<Integer> classIds) {
    }
}
