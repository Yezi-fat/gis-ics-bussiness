package com.example.mapchange.common.core.api;

/**
 * 统一错误码（FR-2.7，设计 §6.1 全量）。
 * 每个枚举携带默认 HTTP 状态码，与 §6.1 表一一对应。
 */
public enum ErrorCode {
    /** 影像格式/大小不合法、参数缺失、tile_range 与 geo_extent 不一致 */
    INVALID_INPUT(400),
    /** 输入规模超当前部署形态可处理上限（评审 R-04：同步超 sync-max 自动转异步，不报错） */
    INPUT_TOO_LARGE(400),
    /** 要素类别超出目录（FR-6.6） */
    UNSUPPORTED_ELEMENTS(400),
    /** 多期时序颠倒/无期次（FR-8.1） */
    PERIOD_ORDER_INVALID(400),
    /** 多期影像范围不一致（FR-8.7） */
    GEO_EXTENT_MISMATCH(400),
    /** 未认证 */
    UNAUTHORIZED(401),
    /** 无权限 */
    FORBIDDEN(403),
    /** 非 QUEUED 态取消（FR-2.8） */
    TASK_NOT_CANCELLABLE(409),
    /** 配准失败/偏差过大（FR-7.7） */
    ALIGNMENT_FAILED(422),
    /** 限流 */
    RATE_LIMITED(429),
    /** 推理不可用且未允许降级（FR-5.3） */
    INFERENCE_UNAVAILABLE(502),
    /** NLP 不可用或开关关闭（FR-9.6/10.5） */
    NLU_UNAVAILABLE(503),
    /** 推理超时 */
    INFERENCE_TIMEOUT(504),
    /** 兜底 */
    INTERNAL_ERROR(500);

    private final int defaultHttpStatus;

    ErrorCode(int defaultHttpStatus) {
        this.defaultHttpStatus = defaultHttpStatus;
    }

    public int defaultHttpStatus() {
        return defaultHttpStatus;
    }
}
