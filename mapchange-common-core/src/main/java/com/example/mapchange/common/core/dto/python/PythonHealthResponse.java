package com.example.mapchange.common.core.dto.python;

import java.util.List;

/**
 * Python 服务健康上报（FR-5.6/5.7 环境探测）。
 * gpuAvailable/显存用于三级解析；models 为各模型就绪状态（FR-5.5）。
 */
public record PythonHealthResponse(String status, boolean gpuAvailable, Long gpuMemoryMb,
                                   Integer cpuCores, List<ModelReady> models) {

    public record ModelReady(String modelName, String modelVersion, boolean ready) {
    }
}
