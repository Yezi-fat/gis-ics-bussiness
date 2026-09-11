package com.example.mapchange.analysis.inference;

import com.example.mapchange.common.core.api.BizException;
import com.example.mapchange.common.core.api.ErrorCode;
import com.example.mapchange.common.core.dto.python.ChangeDetectRequest;
import com.example.mapchange.common.core.dto.python.ChangeMaskResponse;
import com.example.mapchange.common.core.dto.python.SegmentationRequest;
import com.example.mapchange.common.core.dto.python.SegmentationResponse;

/**
 * 本地降级结果包装：携带响应与降级标记（FR-3.3 写入 task）。
 */
public record FallbackResult<T>(T response, boolean degraded) {

    public static <T> FallbackResult<T> direct(T response) {
        return new FallbackResult<>(response, false);
    }

    public static <T> FallbackResult<T> degraded(T response) {
        return new FallbackResult<>(response, true);
    }

    /** 降级不可用时抛出规范错误 */
    public static BizException unavailable(String detail) {
        return new BizException(ErrorCode.INFERENCE_UNAVAILABLE, "推理服务不可用且未允许降级: " + detail);
    }
}
